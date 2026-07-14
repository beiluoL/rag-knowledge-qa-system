package com.example.ragkb.config;

import com.example.ragkb.model.entity.KbCategory;
import com.example.ragkb.model.entity.KnowledgeBase;
import com.example.ragkb.model.entity.User;
import com.example.ragkb.model.enums.UserRole;
import com.example.ragkb.repository.DocumentRepository;
import com.example.ragkb.repository.KbCategoryRepository;
import com.example.ragkb.repository.KnowledgeBaseRepository;
import com.example.ragkb.repository.UserRepository;
import com.example.ragkb.service.ConversationConfigService;
import com.example.ragkb.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KbCategoryRepository kbCategoryRepository;
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final PasswordEncoder passwordEncoder;
    private final ConversationConfigService conversationConfigService;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.seed.sample-docs:true}")
    private boolean seedSampleDocs;

    @Override
    public void run(String... args) {
        initAdmin();
        seedCategories();
        seedKnowledgeBases();
        try {
            seedSampleDocuments();
        } catch (Exception e) {
            log.warn("示例文档灌库出现异常（不影响启动）：{}", e.getMessage());
        }
        // 从 DB 回填对话配置（ai-mode / ai-framework），使重启保留上次切换值
        try {
            conversationConfigService.loadFromDbOnStartup();
        } catch (Exception e) {
            log.warn("对话配置回填出现异常（不影响启动）：{}", e.getMessage());
        }
    }

    private void initAdmin() {
        if (userRepository.findByUsername(adminUsername).isEmpty()) {
            User admin = User.builder()
                    .username(adminUsername)
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .role(UserRole.ADMIN)
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            log.info("默认管理员账号已创建: {} / {}", adminUsername, adminPassword);
        } else {
            log.info("管理员账号已存在，跳过初始化");
        }
    }

    /** 预置分类（动态分类的基础，用户可在前台继续新增） */
    private void seedCategories() {
        String[][] cats = {
                {"技术文档", "技术文档、API/SDK、工程规范"},
                {"产品手册", "产品、运营、业务话术"},
                {"法律法规", "合同、合规、条文"},
                {"教育培训", "课程、考试、培训材料"},
                {"医疗健康", "医学指南、健康科普"},
                {"编程与开发", "编程语言、框架、大模型等开发类知识"}
        };
        for (String[] c : cats) {
            if (kbCategoryRepository.findByName(c[0]).isEmpty()) {
                kbCategoryRepository.save(KbCategory.builder().name(c[0]).description(c[1]).build());
            }
        }
        log.info("分类已就绪，共 {} 个", kbCategoryRepository.count());
    }

    /** 预置系统知识库：原 5 个领域 + 编程与开发（父）及其 5 个子库，形成树 */
    private void seedKnowledgeBases() {
        seedKb("技术文档", "技术文档、API/SDK、工程规范类知识库", "技术文档");
        seedKb("产品手册", "产品、运营、业务话术类知识库", "产品手册");
        seedKb("法律法规", "合同、合规、条文类知识库", "法律法规");
        seedKb("教育培训", "课程、考试、培训材料类知识库", "教育培训");
        seedKb("医疗健康", "医学指南、健康科普类知识库", "医疗健康");

        Long devParentId = ensureKb("编程与开发", "编程语言、框架、大模型等开发类知识库", "编程与开发", null);
        seedChildKb("Java学习", "Java 语言基础、JVM、并发与常用生态", devParentId, "编程与开发");
        seedChildKb("Python学习", "Python 语法、标准库与数据/AI 生态", devParentId, "编程与开发");
        seedChildKb("CSS学习", "CSS 布局、动画、响应式与现代特性", devParentId, "编程与开发");
        seedChildKb("Vue学习", "Vue3 组合式 API、组件与状态管理", devParentId, "编程与开发");
        seedChildKb("React学习", "React 组件化、Hooks、路由与状态管理", devParentId, "编程与开发");
        seedChildKb("大模型学习", "大模型原理、提示工程、RAG 与微调", devParentId, "编程与开发");
        log.info("知识库已就绪");
    }

    private void seedKb(String name, String desc, String categoryName) {
        ensureKb(name, desc, categoryName, null);
    }

    private void seedChildKb(String name, String desc, Long parentId, String categoryName) {
        ensureKb(name, desc, categoryName, parentId);
    }

    private Long ensureKb(String name, String desc, String categoryName, Long parentId) {
        return knowledgeBaseRepository.findByName(name)
                .map(KnowledgeBase::getId)
                .orElseGet(() -> knowledgeBaseRepository.save(KnowledgeBase.builder()
                        .name(name)
                        .description(desc)
                        .categoryId(categoryId(categoryName))
                        .parentId(parentId)
                        .isSystem(true)
                        .sortOrder(0)
                        .build()).getId());
    }

    private Long categoryId(String name) {
        return kbCategoryRepository.findByName(name).map(KbCategory::getId).orElse(null);
    }

    /**
     * 为每个预置知识库灌入示例文档，使学习卡片与 RAG 检索开箱即用。
     * 已存在文档的知识库会跳过；单篇失败不影响整体（try/catch 包裹）。
     */
    private void seedSampleDocuments() {
        if (!seedSampleDocs) {
            log.info("示例文档灌库已关闭（app.seed.sample-docs=false）");
            return;
        }
        Long uploaderId = userRepository.findByUsername(adminUsername)
                .map(User::getId).orElse(1L);

        Map<String, List<String[]>> samples = buildSampleDocs();
        java.util.concurrent.atomic.AtomicInteger total = new java.util.concurrent.atomic.AtomicInteger(0);
        for (Map.Entry<String, List<String[]>> entry : samples.entrySet()) {
            String kbName = entry.getKey();
            knowledgeBaseRepository.findByName(kbName).ifPresent(kb -> {
                int inserted = 0;
                for (String[] d : entry.getValue()) {
                    // 按标题幂等：已存在同标题文档则跳过，便于后续仅追加新知识点而不覆盖已有内容
                    if (documentRepository.existsByTitleAndKnowledgeBaseId(d[0], kb.getId())) {
                        continue;
                    }
                    try {
                        documentService.seedTextDocument(d[0], d[1], kb.getId(), uploaderId);
                        total.incrementAndGet();
                        inserted++;
                    } catch (Exception e) {
                        log.warn("示例文档灌库失败（知识库={}，标题={}）：{}", kbName, d[0], e.getMessage());
                    }
                }
                if (inserted > 0) {
                    log.info("知识库『{}』示例文档灌库完成（新增 {} 篇）：{}", kbName, inserted, entry.getValue().size());
                }
            });
        }
        if (total.get() > 0) log.info("示例文档灌库总计 {} 篇", total.get());
    }

    /** 预置知识库 → 示例文档（标题 + 正文）。每个知识点一篇文档，天然成为一张学习卡片。 */
    private Map<String, List<String[]>> buildSampleDocs() {
        Map<String, List<String[]>> map = new java.util.LinkedHashMap<>();

        // 编程与开发（父库）：通用编程素养
        map.put("编程与开发", List.of(
                new String[]{"编程范式概览：命令式与声明式",
                        "编程范式是组织代码的基本风格。命令式编程（如 C、早期 Java）强调『怎么做』，通过变量、循环、条件逐步改变状态；声明式编程（如 SQL、函数式、React）强调『要什么』，由运行时决定执行过程。主流范式还有面向对象（封装/继承/多态）、函数式（纯函数、不可变、高阶函数）、响应式（数据流驱动）。现代语言多范式融合，理解范式有助于在合适场景选择清晰、可维护的结构。"},
                new String[]{"版本控制 Git 核心概念",
                        "Git 是分布式版本控制系统，每个开发者拥有完整仓库副本。核心概念：工作区、暂存区（index）、本地仓库、远程仓库。常用流程：git add 将改动纳入暂存区，git commit 生成快照，git push 同步到远程，git pull 拉取并合并。分支（branch）用于并行开发，git merge 合并、git rebase 变基。通过 .gitignore 忽略无关文件，用 commit 信息记录『为什么改』而非『改了什么』。"},
                new String[]{"时间复杂度与空间复杂度",
                        "复杂度衡量算法随输入规模增长的资源消耗。时间复杂度用大 O 表示最坏情况下的步数阶数，如 O(1) 常数、O(log n) 对数（二分查找）、O(n) 线性、O(n log n)（快排/归并）、O(n²)（冒泡/双重循环）。空间复杂度衡量额外内存。分析时关注『增长趋势』而非常数因子，优先选择低阶复杂度；当 n 很大时，O(n²) 会显著慢于 O(n log n)。"},
                new String[]{"调试与单元测试",
                        "调试是定位与修复缺陷的过程：先稳定复现，再用断点/日志观察变量与调用栈，缩小到最小可复现单元，最后修正并验证。单元测试针对最小功能单元（函数/方法）验证正确性，遵循『Arrange-Act-Assert』结构，追求独立、可重复、快速。测试覆盖率反映被执行代码比例，但不等于质量；边界值、异常路径与典型场景都应覆盖。"},
                new String[]{"正则表达式基础",
                        "正则表达式（Regex）用模式匹配文本。基础元字符：. 匹配任意单字符，\\d 数字、\\w 单词字符、\\s 空白；量词 * 0+ 次、+ 1+ 次、? 0/1 次、{n,m} 区间；锚点 ^ 行首、$ 行尾；字符类 [abc]、[^abc] 取反；分组 (...) 与选择 a|b。贪婪匹配尽量多、加 ? 变懒惰。常用于校验、提取与替换。注意复杂正则难维护，必要时加注释或改用解析器。"}));

        // Java 学习
        map.put("Java学习", List.of(
                new String[]{"Java 集合框架概述",
                        "Java 集合框架分为三大类：List 有序可重复（ArrayList 基于数组、随机访问快；LinkedList 基于链表、插入删除快）；Set 不可重复（HashSet 基于哈希、查询 O(1)，TreeSet 有序）；Map 键值对（HashMap 哈希实现、允许 null，TreeMap 按 key 排序，ConcurrentHashMap 线程安全）。选择集合时应根据读写比例、是否去重、是否需要排序来决定。"},
                new String[]{"JVM 内存模型",
                        "JVM 运行时数据区包括：堆（所有线程共享，存放对象实例，是 GC 主战场）、方法区/元空间（类信息、常量）、虚拟机栈（每个线程私有，存放栈帧与局部变量）、本地方法栈、程序计数器。垃圾回收主要针对堆，常见算法有标记-清除、复制、标记-整理；分代回收将堆分为新生代与老年代，分别采用不同策略提升效率。"},
                new String[]{"并发与多线程",
                        "Java 通过 Thread 类与 Runnable/Callable 接口创建线程，ExecutorService 线程池统一管理复用。共享变量需同步：synchronized 关键字保证方法/代码块互斥，volatile 保证可见性但不保证原子性，java.util.concurrent.atomic 提供原子类，Lock（如 ReentrantLock）更灵活。并发容器（ConcurrentHashMap、CopyOnWriteArrayList）减少锁竞争。核心难点是竞态条件与死锁，应通过缩小临界区、固定加锁顺序来规避。"},
                new String[]{"异常处理机制",
                        "Java 异常分受检异常（Exception 子类但非 RuntimeException，必须 try-catch 或 throws 声明）与非受检异常（RuntimeException 及其子类，如空指针、越界，可不处理）。try-catch-finally 捕获并处理，finally 始终执行（用于关闭资源）；try-with-resources 自动关闭实现 AutoCloseable 的资源。自定义异常继承 Exception/RuntimeException。抛出早、捕获晚，不要吞掉异常，日志记录上下文有助于排错。"},
                new String[]{"Stream 流式 API",
                        "Java 8 Stream 以声明式方式处理集合：通过 filter（过滤）、map（映射）、flatMap（扁平化）、sorted（排序）、distinct（去重）、limit/skip（截取）等中间操作，配合 collect（收集为 List/Map）、count、reduce、forEach 等终止操作。流不可复用，且惰性求值（终止操作才触发）。并行流 parallelStream() 利用多核，但需注意线程安全与合并开销，并非总是更快。"},
                new String[]{"JVM 垃圾回收",
                        "JVM 自动管理内存，垃圾回收（GC）回收不可达对象。可达性分析从 GC Roots（栈引用、静态变量等）出发标记存活对象。分代假说认为多数对象朝生夕死，故堆分新生代（Eden+Survivor）与老年代。Minor GC 回收新生代（复制算法），Major/Full GC 回收老年代（标记-整理）。常见收集器：G1 平衡吞吐与停顿，ZGC/Shenandoah 追求极低停顿。调优关注吞吐、停顿时间与内存占用。"}));

        // Python 学习
        map.put("Python学习", List.of(
                new String[]{"Python 虚拟环境",
                        "虚拟环境用于隔离不同项目的依赖，避免版本冲突。使用 venv 创建：python -m venv .venv 生成独立环境；在 Windows 上执行 .venv\\Scripts\\activate 激活，在 macOS/Linux 上执行 source .venv/bin/activate。激活后 pip install 的包仅作用于当前环境。requirements.txt 通过 pip freeze 导出，pip install -r requirements.txt 复现环境。"},
                new String[]{"列表推导式",
                        "列表推导式用简洁语法从可迭代对象生成列表：[表达式 for 项 in 可迭代对象 if 条件]。例如 [x*x for x in range(10) if x%2==0] 得到偶数的平方。相比等价 for 循环，它更短且通常更快。但应避免写过长的推导式——当逻辑复杂时，可读性优先，改用常规循环。字典推导与集合推导语法类似。"},
                new String[]{"装饰器",
                        "装饰器是接收函数、返回新函数的高阶函数，用 @ 语法糖在定义处包装目标函数，常用于日志、鉴权、缓存、计时。本质：@dec 等价于 fn = dec(fn)。带参数的装饰器需三层嵌套（外层收参数、中层收函数、内层收参数）。functools.wraps 保留原函数元信息（名称、文档）。类也可作装饰器（实现 __call__）。理解装饰器关键在于『函数是对象，可被传递与替换』。"},
                new String[]{"异常处理",
                        "Python 用 try-except-else-finally 处理异常：try 包裹可能出错代码，except 捕获指定异常（可多分支，小异常在前），else 在无异常时执行，finally 始终执行（清理资源）。raise 主动抛出，assert 用于调试断言。常见内置异常：ValueError、TypeError、KeyError、IndexError。捕获应具体而非裸 except:（会吞掉 KeyboardInterrupt 等）。自定义异常继承 Exception。异常用于『异常情况』而非正常流程控制。"},
                new String[]{"异步 asyncio",
                        "asyncio 提供单线程并发的异步编程模型。async def 定义协程，await 挂起等待可等待对象（coroutine、Task、Future）而不阻塞事件循环。asyncio.run() 启动主协程，asyncio.gather()/create_task() 并发调度多个任务。适用于 I/O 密集型（网络、文件）而非 CPU 密集型（后者用多进程）。注意：同步阻塞调用会卡住整个事件循环；需配合 aiohttp/aiomysql 等异步库才能体现优势。"},
                new String[]{"常用数据结构与标准库",
                        "Python 内置结构：list（可变有序）、tuple（不可变）、dict（哈希表键值对）、set（去重无序）、str（不可变序列）。collections 提供 defaultdict、Counter、deque、OrderedDict 等增强结构；itertools 提供链式、分组、排列组合等高效迭代器；functools 提供 lru_cache 缓存、partial 偏函数。字符串格式化推荐 f-string。理解可变与不可变、浅拷贝与深拷贝（copy/deepcopy）能避免许多隐蔽 bug。"}));

        // CSS 学习
        map.put("CSS学习", List.of(
                new String[]{"Flexbox 布局核心",
                        "Flexbox 是一维布局模型。容器设 display:flex 后，子项沿主轴排列（flex-direction: row 横向 / column 纵向）。justify-content 控制主轴对齐（flex-start/center/space-between 等），align-items 控制交叉轴对齐。flex: 1 让子项按比例伸缩。flex-wrap: wrap 允许换行。它特别适合导航栏、卡片排列、垂直居中等场景，比浮动布局更直观可控。"},
                new String[]{"CSS 自定义属性（变量）",
                        "CSS 变量（自定义属性）以 -- 开头定义，如 --brand: #4f46e5，通过 var(--brand) 使用。:root 中定义即为全局变量。变量可继承，子元素可覆盖父级定义，从而实现主题切换：切换 class 即可整体换色。变量还能结合 calc() 做计算。相比预处理器变量，原生变量在运行时可被 JavaScript 动态修改，适合暗色模式与动态主题。"},
                new String[]{"Grid 网格布局",
                        "CSS Grid 是二维布局系统，同时控制行与列。display: grid 后，用 grid-template-columns/rows 定义轨道（可用 fr 比例单位、repeat()、minmax()），gap 设间距，grid-template-areas 用命名区域直观排版。子项用 grid-column/grid-row 指定跨度，justify/align 系列属性分别在容器与项目层对齐。相比 Flexbox（一维），Grid 擅长整体页面骨架与复杂二维结构，二者常结合使用。"},
                new String[]{"过渡与动画",
                        "transition 让属性变化平滑过渡：指定属性、时长、缓动函数（ease/linear/cubic-bezier）与延迟，适合 hover、状态切换等简单变化。@keyframes 定义动画关键帧，animation 属性控制名称、时长、迭代次数（infinite）、方向（alternate）与填充模式。优先使用 transform/opacity 做动画（GPU 加速、不触发重排），避免动画 width/height/top 等导致性能问题。尊重 prefers-reduced-motion 可访问性偏好。"},
                new String[]{"响应式与媒体查询",
                        "响应式设计使页面适配不同设备。核心手段：流式布局（百分比/flex/grid）、相对单位（rem/em/vw）、弹性图片（max-width:100%），以及媒体查询 @media 按视口宽度（断点）应用不同样式。移动优先（mobile-first）先写小屏样式，再用 min-width 断点增强大屏。现代也可用容器查询（@container）按容器尺寸而非视口响应。断点常取 768/1024px，但应以内容而非设备为准。"},
                new String[]{"BEM 命名规范",
                        "BEM（Block-Element-Modifier）是一种 CSS 命名方法论，提升可维护性。Block 独立组件（.card），Element 组件的一部分（.card__title，双下划线），Modifier 变体或状态（.card--featured、.card__btn--disabled，双连字符）。规则：类名语义化、扁平无嵌套依赖、通过组合而非后代选择器控制样式。优点是避免命名冲突、结构清晰、便于协作；缺点是类名较长。配合 CSS Modules / SCSS 可进一步隔离作用域。"}));

        // Vue 学习
        map.put("Vue学习", List.of(
                new String[]{"Vue3 组合式 API",
                        "Vue3 推荐组合式 API：在 script setup 中直接编写。ref 用于基本类型响应式（访问需 .value），reactive 用于对象。computed 派生状态，watch/watchEffect 侦听变化。生命周期钩子如 onMounted 在 setup 中直接调用。组合式 API 将同一逻辑的状态与函数聚合在一起，相比选项式 API 更利于逻辑复用与 TypeScript 支持。"},
                new String[]{"响应式原理",
                        "Vue3 使用 Proxy 实现响应式：对对象进行代理，读取时通过 track 收集依赖（哪些 effect 用到该属性），修改时通过 trigger 通知依赖重新执行。相比 Vue2 的 Object.defineProperty，Proxy 能监听属性新增/删除与数组索引变化，无需 $set。深层对象通过递归代理实现。响应式系统让数据变化自动驱动视图更新，是框架渲染的核心机制。"},
                new String[]{"组件通信",
                        "Vue 组件通信：父→子通过 props 传值（子用 defineProps 声明、单向流动）；子→父通过 emits 触发自定义事件（defineEmits + emit）。兄弟/跨层：provide/inject 依赖注入适合祖先向后代透传；全局用 Pinia。v-model 是 props+event 的语法糖（默认 modelValue/update:modelValue）。避免直接修改 props；需要双向绑定时在子组件内维护 local 副本或用 computed 的 get/set。保持数据单向流，调试更清晰。"},
                new String[]{"Pinia 状态管理",
                        "Pinia 是 Vue 官方推荐的状态库，替代 Vuex。核心概念：defineStore 定义仓库，可含 state（响应式数据）、getters（派生状态，类似计算属性）、actions（修改状态或异步逻辑）。组件中用 storeToRefs 解构保持响应式，直接调用 action。相比 Vuex，Pinia 无 mutations、TS 友好、更简洁。适合跨组件/跨路由共享的状态（用户、购物车、主题）。注意只把真正需要共享的数据放进 store，局部状态留在组件内。"},
                new String[]{"Vue Router",
                        "Vue Router 实现前端路由：createRouter 配置 routes 数组（path→component），<router-view> 渲染匹配组件，<router-link> 声明式跳转。动态路由 /user/:id 用 useRoute().params 取参；编程式用 useRouter().push。嵌套路由通过 children 与子 <router-view> 实现布局复用。导航守卫（beforeEach）可做登录校验、权限。路由懒加载（() => import()）配合代码分割减小首包体积。"},
                new String[]{"生命周期与侦听",
                        "Vue 组件生命周期：创建（setup/created）、挂载（onMounted DOM 就绪）、更新（onUpdated）、卸载（onUnmounted 清理定时器/订阅）。组合式 API 中直接在 setup 内用 onXxx 钩子注册。侦听变化：watch(源, 回调, 选项) 侦听单个/多个源，watchEffect 立即执行并自动追踪依赖；flush:'post' 可在 DOM 更新后执行。按需选择在 onMounted 还是 watch 中发请求：依赖参数变化时用 watch。及时在 onUnmounted 取消异步与监听，防内存泄漏。"}));

        // React 学习（新增）
        map.put("React学习", List.of(
                new String[]{"JSX 与组件基础",
                        "JSX 是 JavaScript 的语法扩展，允许在 JS 中书写类 HTML 结构，最终被编译为 React.createElement 调用。组件是 UI 的复用单元，分为函数组件（推荐，普通函数返回 JSX）与类组件。组件名必须大写开头以便区分原生标签。Props 是父传给子的只读数据，子组件不能直接修改；状态（state）由组件自身持有。React 通过虚拟 DOM 与协调（reconciliation） diff 最小化真实 DOM 更新。"},
                new String[]{"Props 与 State",
                        "Props（属性）是组件外部传入的只读数据，父组件通过属性向子组件传值，形成单向数据流，子组件不应修改 props。State（状态）是组件内部可变数据，变化会触发重新渲染；在函数组件中用 useState 声明。区分二者：数据属于谁、谁负责修改。频繁变化的 UI 数据应放入 state；跨层级共享可用状态提升（lifting state up）或 Context/状态库。"},
                new String[]{"Hooks：useState 与 useEffect",
                        "Hooks 是 React 16.8 引入的函数式特性，让函数组件拥有状态与生命周期能力，且可复用逻辑。useState(initial) 返回 [值, setter]，调用 setter 触发重渲染。useEffect(fn, deps) 在渲染后执行副作用（数据请求、订阅、DOM 操作），依赖数组决定何时重新执行；空数组仅一次，无依赖每次渲染都执行；返回清理函数用于取消订阅。遵循『依赖完整、避免过期闭包』原则。"},
                new String[]{"组件通信方式",
                        "React 组件通信遵循单向数据流：父→子通过 props 传值；子→父通过 props 传入的回调函数（子调用父函数并传参）；兄弟组件通过提升共享状态到最近共同父级；跨多层用 Context 提供/消费，避免逐层透传（prop drilling）；全局状态用 Redux/Zustand 等。事件处理中注意 this 绑定（函数组件无需）。保持数据单一来源（single source of truth）可减少不一致。"},
                new String[]{"路由与状态管理",
                        "React Router 提供声明式路由：<Routes>/<Route path element> 映射 URL 与组件，useParams/useNavigate 访问参数与编程跳转，嵌套路由实现布局复用。状态管理：轻量用 Context+useReducer；大型应用引入 Redux Toolkit（单一 store、切片、不可变更新）或 Zustand（轻量、钩子式）。选择取决于共享范围与复杂度——过度使用全局状态会增加耦合。"},
                new String[]{"性能优化",
                        "React 性能优化重点在减少不必要的重渲染与昂贵计算：React.memo 缓存组件（props 浅比较）、useMemo 缓存计算结果、useCallback 缓存函数引用；列表渲染必须加稳定 key（非 index）；虚拟列表（react-window）处理超长列表；代码分割用 React.lazy + Suspense 按需加载；避免在渲染中创建新对象/函数导致子组件无谓更新。"}));

        // 大模型学习
        map.put("大模型学习", List.of(
                new String[]{"Transformer 注意力机制",
                        "Transformer 以自注意力机制为核心。每个 token 映射为查询 Q、键 K、值 V 三个向量；注意力分数由 Q 与所有 K 的点积经缩放 softmax 得到，再对 V 加权求和，使模型关注上下文中最相关的词。多头注意力并行计算多组 Q/K/V，捕捉不同子空间关系。相比 RNN，注意力可并行计算且能直接建模远距离依赖，是现代大模型的基础。"},
                new String[]{"Prompt 工程基础",
                        "Prompt 工程通过设计输入引导大模型产出期望结果。基本结构包含：角色（如『你是一名资深工程师』）、任务（清晰描述要做什么）、上下文（必要背景与约束）、示例（少样本提升稳定性）、输出格式（JSON/列表等）。技巧：指令明确具体、给步骤、限定范围、要求逐步推理（CoT）。好的 Prompt 应可复用、可评测，并随模型迭代持续优化。"},
                new String[]{"RAG 检索增强生成",
                        "RAG（Retrieval-Augmented Generation）在生成前先从知识库检索相关片段，拼入提示词再交给大模型，从而基于私有/最新数据回答，缓解幻觉与知识截止问题。流程：文档切分→向量化→存入向量库；用户提问时将其向量化并相似检索 Top-K 片段；与问题组装成上下文送入 LLM。关键在切分粒度、检索质量（混合检索/BGE 重排）与上下文组织。适用于企业问答、文档助手等对事实准确性要求高的场景。"},
                new String[]{"微调基础",
                        "微调（Fine-tuning）用领域数据继续训练预训练模型，使其适配特定任务或风格。全参数微调更新全部权重，成本高；参数高效微调（PEFT）如 LoRA/QLoRA 只训练低秩适配器，显存友好、可插拔。流程：准备指令-回答配对数据（SFT）、选基座与超参（学习率、epoch）、训练并评估。与 RAG 互补：RAG 注入外部知识，微调注入能力与风格。小数据下轻量微调常优于从头训练。"},
                new String[]{"向量数据库",
                        "向量数据库专门存储与检索高维向量（embedding），支撑语义搜索与 RAG。核心能力：近似最近邻（ANN）检索（如 HNSW、IVF-PQ 索引）在海量向量中快速找相似项；常配合元数据过滤。主流方案：pgvector（PostgreSQL 扩展，易运维）、Milvus、Qdrant、Chroma、Faiss。选型关注规模、延迟、成本与运维复杂度。衡量指标用余弦相似度/内积/欧氏距离，需与 embedding 模型的训练度量一致。"},
                new String[]{"模型评估",
                        "大模型评估衡量能力与风险：能力维度含准确性、相关性、流畅性、安全性、推理与工具调用；方法分自动指标（BLEU/ROUGE 偏表面、基于 LLM 的裁判如 GPT-4-as-judge 更贴近人类）与人工评测。常用基准：MMLU（学科知识）、GSM8K（数学）、HumanEval（代码）、MT-Bench（多轮对话）。RAG 还需评估检索召回与上下文利用率。评估应覆盖边界与对抗样本，并持续回归以防迭代退化。"}));

        return map;
    }
}
