package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File

/**
 * 校验「地图 SDK 继承链」是否还能安全运行在目标版本上。
 *
 * ## 它在防什么
 *
 * 版本适配用的是「继承链 + 只写差异」：850 的适配代码按 850 的 API 编译，
 * 却要在 850 / 910 的 SDK 上运行。一旦 910 移除了某个 API（`MapView(context)`、
 * `searchPOIAsyn`、`PoiItem.uid`…），而 910 的 binding 又忘了覆写对应方法，
 * 就会走父类实现 → 运行时 `NoSuchMethodError`。
 *
 * 版本改成「包」之后，编译期不再按版本隔离 API（同一个模块只有一条 classpath），
 * 所以这条检查从「锦上添花」变成了**主要防线**。
 *
 * ## 判定规则
 *
 * 对每条继承链、每个版本 v：
 * 1. 取出 v 这条链上的所有类（v 的 binding + 其祖先）；
 * 2. 扫描这些类的字节码，找出**所有对厂商 SDK 成员的引用**；
 * 3. 若某个引用在版本 v 的 API 面里**不存在**（被移除，或 v 还没有），则：
 *    - 它所在的「包含方法」必须被链上某个子类覆写，**且该覆写不能再 `super.` 调回父类实现**
 *      （覆写后这段代码根本不会执行）→ 通过；
 *    - 只有覆写、但覆写里 `super.xxx()` 又会走回老实现 → 报「覆写仍调 super」；
 *    - 完全没有覆写 → 报错，并按「能不能修」分类：构造函数 / `init` / `val` 初始化式 /
 *      `final` 方法 → 不可修（子类没有覆写点）；`open` 方法但没人覆写 → 缺覆写。
 *
 * API 面来自影子模块的 `@ApiSince` / `@ApiRemoved` 注解（见 `map-sdk-amap` / `map-sdk-baidu`），
 * 因此这份知识只有一份，不会和代码脱节。
 *
 * 另外支持 `mustOverride`：签名没变但语义变了的 API（如 910 的 `initialize` 必须先声明隐私），
 * 静态 API 面 diff 抓不到，直接声明「版本 v 必须覆写某方法」。
 */
abstract class VerifyMapSdkChainTask : DefaultTask() {

    /** 链名，仅用于报告与日志（amap / baidu） */
    @get:Input
    abstract val chainName: Property<String>

    /** SDK 版本，**必须按从低到高排列**（850, 910） */
    @get:Input
    abstract val versions: ListProperty<String>

    /** 版本 → 该版本 binding 的内部类名（`com/.../Amap850Binding`） */
    @get:Input
    abstract val bindingClasses: MapProperty<String, String>

    /** 厂商 SDK 的包前缀（`com/amap/api/`），用于从所有引用里筛出 SDK 引用 */
    @get:Input
    abstract val sdkPackages: ListProperty<String>

    /** 版本 → 该版本必须覆写的方法名（签名没变、语义变了的 API） */
    @get:Input
    abstract val mustOverride: MapProperty<String, List<String>>

    /** 影子 SDK 的 class 输出目录 */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val shadowClasses: ConfigurableFileCollection

    /** 厂商实现模块的 class 输出目录 */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val implClasses: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    // ------------------------------------------------------------------ 模型

    private class Member(val name: String, val desc: String) {
        var since: String? = null
        var removedIn: String? = null
    }

    private class Surface(val internalName: String) {
        var superName: String? = null
        var access: Int = 0
        var since: String? = null
        var removedIn: String? = null
        val methods = mutableMapOf<String, Member>()
        val fields = mutableMapOf<String, Member>()
    }

    private class ImplClass(val internalName: String) {
        var superName: String? = null
        var access: Int = 0
        val declared = mutableSetOf<String>()
        val methods = mutableMapOf<String, Int>()

        /** 方法 key（名称+描述符）→ 该方法体内通过 `invokespecial` 调用的父类方法签名集合 */
        val superCalls = mutableMapOf<String, MutableSet<String>>()
    }

    private class Ref(val owner: String, val name: String, val desc: String)

    private class Site(
        val owner: String,
        val methodName: String,
        val methodDesc: String,
        val ref: Ref,
    )

    private class Problem(
        val version: String,
        val className: String,
        val methodName: String,
        val methodDesc: String,
        val symbol: String,
        val kind: String,
        val hint: String,
    )

    // ------------------------------------------------------------------ 主流程

    private val order: List<String> get() = versions.get()

    @TaskAction
    fun verify() {
        val name = chainName.get()
        val allVersions = order
        require(allVersions.isNotEmpty()) { "继承链 $name 的 versions 不能为空" }

        val prefixes = sdkPackages.get()
        val surfaces = readSurfaces(prefixes)
        val impls = readImpls()

        if (surfaces.isEmpty()) {
            throw GradleException(
                "verifyMapSdkChain(${name})：影子 SDK 的 class 目录里没有找到 ${prefixes} 下的类。" +
                    "先执行 :map-sdk-*:compileDebugKotlin。",
            )
        }
        if (impls.isEmpty()) {
            throw GradleException("verifyMapSdkChain(${name})：厂商实现模块没有产出 class 文件。")
        }

        val bindingByVersion = bindingClasses.get()
        val chains = allVersions.associateWith { v ->
            val root = bindingByVersion[v]
                ?: throw GradleException("verifyMapSdkChain(${name})：versions 里的 $v 没有配置 bindingClasses")
            ancestorsOf(root, impls)
        }

        // 1) 收集引用点（含 lambda 等合成类，稍后归因回外部方法）
        val sites = mutableListOf<Site>()
        val syntheticOwner = mutableMapOf<String, Pair<String, String>>() // 合成类 → (外层类, 外层方法名+描述)
        collectSites(prefixes, impls, sites, syntheticOwner)

        // 2) 逐版本判定
        val problems = mutableListOf<Problem>()
        val covered = mutableListOf<String>()

        sites.forEach { site ->
            val (owner, methodName, methodDesc) = resolveSynthetic(site, syntheticOwner)
            val impl = impls[owner] ?: return@forEach
            val versionsOfOwner = allVersions.filter { v -> chains.getValue(v).contains(owner) }
            if (versionsOfOwner.isEmpty()) return@forEach

            val symbol = symbolOf(site.ref)

            versionsOfOwner.forEach { v ->
                if (existsInSurface(site.ref, v, surfaces)) return@forEach

                val chain = chains.getValue(v)
                // chain 从版本 v 的 binding 往祖先排，第一个命中的就是「最派生的覆写」
                val tailOverride = chain.firstOrNull { candidate ->
                    if (candidate == owner || !isSubclassOf(candidate, owner, impls)) return@firstOrNull false
                    val declared = impls[candidate]?.declared ?: return@firstOrNull false
                    if (methodDesc.isEmpty()) {
                        declared.any { it.substringBefore('(') == methodName }
                    } else {
                        declared.contains("$methodName$methodDesc")
                    }
                }

                when {
                    // 覆写了，但覆写里 `super.xxx()` 又会绕回老实现 → 运行期照样 NoSuchMethodError
                    tailOverride != null &&
                        superReaches(tailOverride, owner, methodName, methodDesc, impls) -> {
                        problems += Problem(
                            version = v,
                            className = tailOverride,
                            methodName = methodName,
                            methodDesc = methodDesc,
                            symbol = symbol,
                            kind = "覆写里仍调 super",
                            hint = "${tailOverride.substringAfterLast('/')}.$methodName 覆写后仍调用 super.$methodName()，" +
                                "会执行回 ${owner.substringAfterLast('/')} 里那段「$v 已移除的 API」。" +
                                "破坏性变更的覆写必须整段替换（不要调 super），只有纯增量才允许 super。",
                        )
                    }

                    tailOverride != null -> covered +=
                        "[$v] $symbol @ ${owner.substringAfterLast('/')}.$methodName" +
                            " ← ${tailOverride.substringAfterLast('/')} 覆写且未调 super"

                    else -> problems += classify(v, impl, owner, methodName, methodDesc, symbol)
                }
            }
        }

        // 3) 语义变更：签名没变但必须覆写的成员
        mustOverride.get().forEach { (v, methods) ->
            val binding = bindingByVersion[v] ?: return@forEach
            val chain = chains.getValue(v)
            methods.forEach { m ->
                val declaredSomewhere = chain.any { impls[it]?.declared?.any { d -> d.substringBefore('(') == m } == true }
                val declaredByBinding = impls[binding]?.declared?.any { d -> d.substringBefore('(') == m } == true
                if (!declaredSomewhere || !declaredByBinding) {
                    problems += Problem(
                        version = v,
                        className = binding,
                        methodName = m,
                        methodDesc = "",
                        symbol = "$m()",
                        kind = "语义变更未覆写",
                        hint = "$v 改了 $m 的行为（签名没变、静态 API 面 diff 抓不到），$binding 必须覆写它。",
                    )
                }
            }
        }

        writeReport(name, allVersions, chains, problems, covered)

        if (problems.isNotEmpty()) {
            val lines = buildString {
                appendLine("地图 SDK 继承链校验失败：${name}（${problems.size} 处）")
                problems.forEach { p ->
                    appendLine("  ✗ [${p.version}] ${p.symbol}")
                    appendLine("      ${p.className.substringAfterLast('/')}.${p.methodName} → ${p.kind}")
                    appendLine("      ${p.hint}")
                }
                appendLine("报告：${reportFile.get().asFile.absolutePath}")
            }
            throw GradleException(lines)
        }

        logger.lifecycle(
            "verifyMapSdkChain(${name})：通过。版本 ${allVersions.joinToString(" → ")}；" +
                "已覆写被移除 API 的调用点 ${covered.size} 处。报告：${reportFile.get().asFile.absolutePath}",
        )
    }

    private fun symbolOf(ref: Ref): String {
        val cls = ref.owner.substringAfterLast('/')
        return if (ref.name == "<init>") "$cls(${ref.desc.drop(1).substringBefore(')')})" else "$cls.${ref.name}"
    }

    private fun resolveSynthetic(
        site: Site,
        syntheticOwner: Map<String, Pair<String, String>>,
    ): Triple<String, String, String> {
        var owner = site.owner
        var methodKey = "${site.methodName}${site.methodDesc}"
        var guard = 0
        while (syntheticOwner.containsKey(owner) && guard++ < 8) {
            val (outer, outerKey) = syntheticOwner.getValue(owner)
            owner = outer
            methodKey = outerKey
        }

        var name = methodKey.substringBefore('(')
        var desc = methodKey.substring(name.length)

        // Kotlin 的 lambda 体会编译成外部类里的 private synthetic 方法
        // （`searchPoi$lambda$0`），引用归因回 `searchPoi`。
        // 此时拿不到外部方法的描述符，判定时按「方法名」匹配覆写即可。
        val lambdaMarker = name.indexOf("\$lambda")
        if (lambdaMarker > 0) {
            name = name.substring(0, lambdaMarker)
            desc = ""
        }

        return Triple(owner, name, desc)
    }

    private fun classify(
        version: String,
        impl: ImplClass,
        owner: String,
        methodName: String,
        methodDesc: String,
        symbol: String,
    ): Problem {
        val methodAccess = if (methodDesc.isEmpty()) {
            impl.methods.entries.firstOrNull { it.key.substringBefore('(') == methodName }?.value ?: 0
        } else {
            impl.methods["$methodName$methodDesc"] ?: 0
        }
        val unfixable = methodName == "<init>" || methodName == "<clinit>" ||
            methodAccess and Opcodes.ACC_PRIVATE != 0 ||
            methodAccess and Opcodes.ACC_FINAL != 0 ||
            impl.access and Opcodes.ACC_FINAL != 0

        return if (unfixable) {
            Problem(
                version = version,
                className = owner,
                methodName = methodName,
                methodDesc = methodDesc,
                symbol = symbol,
                kind = "不可修复（无覆写点）",
                hint = "该调用在 ${if (methodName.startsWith("<")) "构造函数 / 静态初始化" else "private 或 final 方法"}，" +
                    "子类无法覆写。必须把它挪进 open 方法（或改成按 capabilities 分支）。",
            )
        } else {
            Problem(
                version = version,
                className = owner,
                methodName = methodName,
                methodDesc = methodDesc,
                symbol = symbol,
                kind = "缺覆写",
                hint = "该 open 方法在 $version 的 binding 链上没有覆写，运行时会执行到这段「老 API 调用」。" +
                    "在 $version 的 binding 里覆写它。",
            )
        }
    }

    // ------------------------------------------------------------------ API 面

    private fun readSurfaces(prefixes: List<String>): Map<String, Surface> {
        val result = mutableMapOf<String, Surface>()
        classFiles(shadowClasses.files).forEach { file ->
            val reader = ClassReader(file.readBytes())
            val surface = Surface(reader.className)
            reader.accept(
                object : ClassVisitor(Opcodes.ASM9) {
                    override fun visit(
                        version: Int,
                        access: Int,
                        name: String,
                        signature: String?,
                        superName: String?,
                        interfaces: Array<out String>?,
                    ) {
                        surface.access = access
                        surface.superName = superName
                    }

                    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? =
                        readVersionAnnotation(descriptor) { field, value ->
                            if (field == SINCE) surface.since = value else surface.removedIn = value
                        }

                    override fun visitMethod(
                        access: Int,
                        name: String,
                        descriptor: String,
                        signature: String?,
                        exceptions: Array<out String>?,
                    ): MethodVisitor {
                        val member = Member(name, descriptor)
                        surface.methods["$name$descriptor"] = member
                        return object : MethodVisitor(Opcodes.ASM9) {
                            override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? =
                                readVersionAnnotation(descriptor) { field, value ->
                                    if (field == SINCE) member.since = value else member.removedIn = value
                                }
                        }
                    }

                    override fun visitField(
                        access: Int,
                        name: String,
                        descriptor: String,
                        signature: String?,
                        value: Any?,
                    ): org.objectweb.asm.FieldVisitor {
                        val member = Member(name, descriptor)
                        surface.fields["$name$descriptor"] = member
                        return object : org.objectweb.asm.FieldVisitor(Opcodes.ASM9) {
                            override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? =
                                readVersionAnnotation(descriptor) { field, v ->
                                    if (field == SINCE) member.since = v else member.removedIn = v
                                }
                        }
                    }
                },
                ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
            )
            if (prefixes.any { reader.className.startsWith(it) }) result[reader.className] = surface
        }
        return result
    }

    private fun readVersionAnnotation(
        descriptor: String,
        apply: (field: String, value: String) -> Unit,
    ): AnnotationVisitor? {
        val field = when {
            descriptor.endsWith("/ApiSince;") -> SINCE
            descriptor.endsWith("/ApiRemoved;") -> REMOVED
            else -> return null
        }
        return object : AnnotationVisitor(Opcodes.ASM9) {
            override fun visit(name: String?, value: Any?) {
                if (name == "value" && value is String) apply(field, value)
            }
        }
    }

    /** 版本 v 的 API 面里是否存在该成员（含父类继承） */
    private fun existsInSurface(ref: Ref, version: String, surfaces: Map<String, Surface>): Boolean {
        var current = surfaces[ref.owner] ?: return false
        var guard = 0
        while (guard++ < 16) {
            if (!presentIn(current.since, current.removedIn, version)) return false
            val key = "${ref.name}${ref.desc}"
            val member = current.methods[key] ?: current.fields[key]
            if (member != null) return presentIn(member.since, member.removedIn, version)
            current = current.superName?.let { surfaces[it] } ?: return false
        }
        return false
    }

    private fun presentIn(since: String?, removedIn: String?, version: String): Boolean {
        val level = order.indexOf(version)
        since?.let {
            val from = order.indexOf(it)
            if (from < 0) {
                throw GradleException("影子层里的 @ApiSince(\"$it\") 不在 versions 列表里，可选：$order")
            }
            if (level < from) return false
        }
        removedIn?.let {
            val to = order.indexOf(it)
            if (to < 0) {
                throw GradleException("影子层里的 @ApiRemoved(\"$it\") 不在 versions 列表里，可选：$order")
            }
            if (level >= to) return false
        }
        return true
    }

    // ------------------------------------------------------------------ 实现模块字节码

    private fun readImpls(): Map<String, ImplClass> {
        val result = mutableMapOf<String, ImplClass>()
        classFiles(implClasses.files).forEach { file ->
            val reader = ClassReader(file.readBytes())
            val impl = ImplClass(reader.className)
            reader.accept(
                object : ClassVisitor(Opcodes.ASM9) {
                    override fun visit(
                        version: Int,
                        access: Int,
                        name: String,
                        signature: String?,
                        superName: String?,
                        interfaces: Array<out String>?,
                    ) {
                        impl.access = access
                        impl.superName = superName
                    }

                    override fun visitMethod(
                        access: Int,
                        name: String,
                        descriptor: String,
                        signature: String?,
                        exceptions: Array<out String>?,
                    ): MethodVisitor? {
                        impl.declared += "$name$descriptor"
                        impl.methods["$name$descriptor"] = access
                        // 必须进方法体：覆写里有没有 `super.xxx()` 决定了父类那段老代码会不会执行
                        val enclosing = "$name$descriptor"
                        return object : MethodVisitor(Opcodes.ASM9) {
                            override fun visitMethodInsn(
                                opcode: Int,
                                owner: String,
                                name2: String,
                                descriptor2: String,
                                isInterface: Boolean,
                            ) {
                                // invoke special 且 owner 不是自己 = super.xxx()（或 super<Interface>.xxx()）
                                if (opcode != Opcodes.INVOKESPECIAL || owner == reader.className) return
                                impl.superCalls.getOrPut(enclosing) { mutableSetOf() } += "$name2$descriptor2"
                            }
                        }
                    }
                },
                ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
            )
            result[reader.className] = impl
        }
        return result
    }

    private fun collectSites(
        prefixes: List<String>,
        impls: Map<String, ImplClass>,
        sites: MutableList<Site>,
        syntheticOwner: MutableMap<String, Pair<String, String>>,
    ) {
        classFiles(implClasses.files).forEach { file ->
            val reader = ClassReader(file.readBytes())
            reader.accept(
                object : ClassVisitor(Opcodes.ASM9) {
                    override fun visitMethod(
                        access: Int,
                        name: String,
                        descriptor: String,
                        signature: String?,
                        exceptions: Array<out String>?,
                    ): MethodVisitor = object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String,
                            name2: String,
                            descriptor2: String,
                            isInterface: Boolean,
                        ) {
                            if (prefixes.any { owner.startsWith(it) }) {
                                sites += Site(reader.className, name, descriptor, Ref(owner, name2, descriptor2))
                            } else if (owner.startsWith("${reader.className}\$") && name2 == "<init>") {
                                // 外部方法里 new 了这个合成类 → 记下归因关系
                                syntheticOwner[owner] = reader.className to "$name$descriptor"
                            }
                        }

                        override fun visitFieldInsn(opcode: Int, owner: String, name2: String, descriptor2: String) {
                            if (prefixes.any { owner.startsWith(it) }) {
                                sites += Site(reader.className, name, descriptor, Ref(owner, name2, descriptor2))
                            }
                        }
                    }
                },
                ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
            )
        }
    }

    // ------------------------------------------------------------------ 继承关系

    private fun ancestorsOf(root: String, impls: Map<String, ImplClass>): Set<String> {
        val result = linkedSetOf<String>()
        var current: String? = root
        var guard = 0
        while (current != null && guard++ < 32) {
            val impl = impls[current] ?: break // 走出本模块（如 java/lang/Object）就停
            if (!result.add(current)) break
            current = impl.superName
        }
        return result
    }

    /**
     * 从最派生的覆写 [start] 往下走，判断老实现（[stopAt]）会不会被 `super.xxx()` 绕回来执行。
     *
     * 每层：该方法体内若没有 `super.同名方法(...)`，说明执行到这里就断了 → 安全；
     * 有则继续往父类走，走到 [stopAt] 说明一路 super 回到了那段老代码 → 不安全。
     *
     * 注意「跳过不实现该方法的中间类」：910 `super.searchPoi()` 落到的是**最近的实现**（850），
     * 中途的 850 根本没声明这个方法，不能因为 850 没有 super 调用就判定安全。
     */
    private fun superReaches(
        start: String,
        stopAt: String,
        methodName: String,
        methodDesc: String,
        impls: Map<String, ImplClass>,
    ): Boolean {
        var current: String? = start
        var guard = 0
        while (current != null && guard++ < 32) {
            if (current == stopAt) return true
            val impl = impls[current] ?: return true
            val declares = if (methodDesc.isEmpty()) {
                impl.declared.any { it.substringBefore('(') == methodName }
            } else {
                impl.declared.contains("$methodName$methodDesc")
            }
            if (declares) {
                val callsSuper = impl.superCalls.entries
                    .filter { matches(it.key, methodName, methodDesc) }
                    .any { e -> e.value.any { matches(it, methodName, methodDesc) } }
                if (!callsSuper) return false
            }
            current = impl.superName
        }
        return true
    }

    /** 方法签名比对；描述符未知（lambda 归因）时只比方法名 */
    private fun matches(key: String, methodName: String, methodDesc: String): Boolean {
        if (key.substringBefore('(') != methodName) return false
        return methodDesc.isEmpty() || key == "$methodName$methodDesc"
    }

    private fun isSubclassOf(candidate: String, base: String, impls: Map<String, ImplClass>): Boolean {
        var current: String? = impls[candidate]?.superName
        var guard = 0
        while (current != null && guard++ < 32) {
            if (current == base) return true
            current = impls[current]?.superName
        }
        return false
    }

    // ------------------------------------------------------------------ 报告

    private fun writeReport(
        name: String,
        allVersions: List<String>,
        chains: Map<String, Set<String>>,
        problems: List<Problem>,
        covered: List<String>,
    ) {
        val file: File = reportFile.get().asFile
        file.parentFile.mkdirs()

        val text = buildString {
            appendLine("# SDK 继承链校验报告：$name")
            appendLine()
            appendLine("- 版本（低 → 高）：${allVersions.joinToString(" → ")}")
            appendLine("- 结论：${if (problems.isEmpty()) "通过 ✅" else "失败 ❌（${problems.size} 处）"}")
            appendLine()
            allVersions.forEach { v ->
                appendLine("## $v 的继承链")
                appendLine()
                chains.getValue(v).forEach { cls ->
                    appendLine("- `${cls.replace('/', '.')}`")
                }
                appendLine()
            }
            appendLine("## 已正确覆写（老 API 调用被隔离在覆写方法内）")
            appendLine()
            if (covered.isEmpty()) appendLine("（无）") else covered.forEach { appendLine("- $it") }
            appendLine()
            appendLine("## 问题")
            appendLine()
            if (problems.isEmpty()) {
                appendLine("（无）")
            } else {
                problems.forEach { p ->
                    appendLine("### [${p.version}] ${p.symbol} — ${p.kind}")
                    appendLine()
                    appendLine("- 位置：`${p.className.replace('/', '.')}.${p.methodName}${p.methodDesc}`")
                    appendLine("- 处理：${p.hint}")
                    appendLine()
                }
            }
        }
        file.writeText(text)
    }

    private fun classFiles(files: Set<File>): List<File> = files.filter { it.exists() }.flatMap { root ->
        root.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
    }

    private companion object {
        const val SINCE = "since"
        const val REMOVED = "removed"
    }
}
