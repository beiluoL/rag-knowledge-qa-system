package com.example.ragkb.config;

import com.example.ragkb.model.entity.KbCategory;
import com.example.ragkb.model.entity.KnowledgeBase;
import com.example.ragkb.model.entity.User;
import com.example.ragkb.model.enums.UserRole;
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
                long existing = documentService.countDocs(kb.getId());
                if (existing > 0) {
                    log.info("知识库『{}』已有 {} 篇文档，跳过灌库", kbName, existing);
                    return;
                }
                for (String[] d : entry.getValue()) {
                    try {
                        documentService.seedTextDocument(d[0], d[1], kb.getId(), uploaderId);
                        total.incrementAndGet();
                    } catch (Exception e) {
                        log.warn("示例文档灌库失败（知识库={}，标题={}）：{}", kbName, d[0], e.getMessage());
                    }
                }
                log.info("知识库『{}』示例文档灌库完成：{} 篇", kbName, entry.getValue().size());
            });
        }
        if (total.get() > 0) log.info("示例文档灌库总计 {} 篇", total.get());
    }

    /** 预置知识库 → 示例文档（标题 + 正文） */
    private Map<String, List<String[]>> buildSampleDocs() {
        Map<String, List<String[]>> map = new java.util.LinkedHashMap<>();
        map.put("技术文档", List.of(
                new String[]{"API 设计规范要点",
                        "RESTful API 应以名词复数表示资源集合，如 /users；用 HTTP 方法表达动作：GET 查询、POST 新建、PUT/PATCH 更新、DELETE 删除。统一返回结构包含 code、message、data 三字段。状态码语义要明确：200 成功、201 已创建、400 参数错误、401 未认证、403 无权限、404 不存在、500 服务异常。分页接口使用 page 与 size 参数，并返回 total 总数。版本通过 URL 前缀（/v1）管理，避免破坏旧客户端。"},
                new String[]{"数据库索引优化常识",
                        "索引本质是一种有序数据结构（多为 B+Tree），用于加速查询并减少全表扫描。最左前缀原则要求联合索引 (a,b,c) 的查询条件需从最左列开始命中。覆盖索引指查询字段都在索引中，可避免回表。避免在索引列上使用函数或隐式类型转换，否则索引失效。慎用 SELECT *，只取需要的列有利于覆盖索引。写多读少的表不宜建过多索引，因为索引会降低写入速度。"}));
        map.put("产品手册", List.of(
                new String[]{"产品发布标准流程",
                        "产品发布需依次完成：1) 明确需求与目标用户，输出产品定义；2) 编写规格说明书，界定功能、性能与边界；3) 设计评审，确认交互与视觉方案；4) 研发实现与自测，覆盖核心路径与异常分支；5) 编写产品手册与 FAQ，沉淀知识便于检索；6) 灰度发布并观测核心指标，验证稳定后全量上线。发布前应通过合规与安全评审，上线后持续收集反馈迭代优化。"},
                new String[]{"营销活动与优惠规则",
                        "优惠常见类型有满减（满 X 减 Y）、折扣（打 N 折）、无门槛立减。叠加规则需明确：同类型优惠一般互斥，不同类型的活动可叠加；优惠门槛指订单实付金额达到 X 才可抵扣；优惠均有有效期，过期作废；限领/限用指每个用户可参与的次数上限。计算顺序一般为：先单品优惠，再跨品类活动优惠，最后使用账户级抵扣。"}));
        map.put("法律法规", List.of(
                new String[]{"个人信息保护法核心要点",
                        "处理个人信息应遵循合法、正当、必要原则，告知并取得个人同意是基本前提（敏感信息需单独明示同意）。最小必要原则要求仅收集与处理目的直接相关的最少信息。个人享有查询、复制、更正、删除、携带其信息的权利。向境外提供个人信息需通过安全评估或认证。处理敏感个人信息（生物识别、医疗健康、金融账户等）须有特定目的与充分必要性。"},
                new String[]{"合同审查清单",
                        "审查合同应关注：1) 主体资格，确认对方营业执照与签约权限；2) 标的，描述清楚货物/服务的内容与标准；3) 价款与支付，金额、币种、节点、发票；4) 履行期限、地点与方式；5) 违约责任，违约金比例与赔偿范围；6) 保密与知识产权归属；7) 争议解决，约定诉讼或仲裁的管辖地；8) 生效与终止条件。关键条款应明确、可量化，避免模糊表述。"}));
        map.put("教育培训", List.of(
                new String[]{"费曼学习法四步",
                        "费曼学习法用输出倒逼输入，分四步：第一步，选定一个概念并假装把它教给完全的外行；第二步，在讲授中遇到卡壳的地方，正是知识盲区，回去重新学习；第三步，简化语言，去掉术语，用类比和日常例子表达；第四步，复盘并结构化笔记。其核心是：如果你不能简单地解释一件事，说明你还没有真正理解它。"},
                new String[]{"课程设计 ADDIE 模型",
                        "ADDIE 是经典教学设计模型，含五个阶段：分析（Analysis）明确学习者特征与教学目标；设计（Design）确定内容结构、评估方式；开发（Development）制作课件与活动；实施（Implementation）开展教学并培训师资；评估（Evaluation）贯穿全程，含形成性与总结性评估。该模型强调以学习目标为导向、持续迭代优化。"}));
        map.put("医疗健康", List.of(
                new String[]{"高血压日常管理",
                        "高血压日常管理要点：1) 低盐饮食，每日食盐摄入不超过 5 克，少吃腌制食品；2) 规律有氧运动，如快走、游泳，每周至少 150 分钟；3) 遵医嘱按时服药，不可擅自停药；4) 定期测量血压并做好记录；5) 控制体重、限酒、戒烟；6) 保持情绪平稳与充足睡眠。若血压持续高于 160/100 或出现头晕胸痛，应及时就医。"},
                new String[]{"成人心肺复苏 CPR 要点",
                        "发现无反应、无呼吸的成人，立即呼叫急救并启动 CPR：将患者仰卧于坚硬平面，双手交叠置于两乳头连线中点，垂直向下按压，深度 5—6 厘米，频率 100—120 次/分，每次按压后让胸廓完全回弹。按压与人工呼吸比例 30:2；若有 AED，尽快取来并按语音提示除颤。持续进行直到患者恢复呼吸或专业人员接手。"}));
        map.put("Java学习", List.of(
                new String[]{"Java 集合框架概述",
                        "Java 集合框架分为三大类：List 有序可重复（ArrayList 基于数组、随机访问快；LinkedList 基于链表、插入删除快）；Set 不可重复（HashSet 基于哈希、查询 O(1)，TreeSet 有序）；Map 键值对（HashMap 哈希实现、允许 null，TreeMap 按 key 排序，ConcurrentHashMap 线程安全）。选择集合时应根据读写比例、是否去重、是否需要排序来决定。"},
                new String[]{"JVM 内存模型",
                        "JVM 运行时数据区包括：堆（所有线程共享，存放对象实例，是 GC 主战场）、方法区/元空间（类信息、常量）、虚拟机栈（每个线程私有，存放栈帧与局部变量）、本地方法栈、程序计数器。垃圾回收主要针对堆，常见算法有标记-清除、复制、标记-整理；分代回收将堆分为新生代与老年代，分别采用不同策略提升效率。"}));
        map.put("Python学习", List.of(
                new String[]{"Python 虚拟环境",
                        "虚拟环境用于隔离不同项目的依赖，避免版本冲突。使用 venv 创建：python -m venv .venv 生成独立环境；在 Windows 上执行 .venv\\Scripts\\activate 激活，在 macOS/Linux 上执行 source .venv/bin/activate。激活后 pip install 的包仅作用于当前环境。requirements.txt 通过 pip freeze 导出，pip install -r requirements.txt 复现环境。"},
                new String[]{"列表推导式",
                        "列表推导式用简洁语法从可迭代对象生成列表：[表达式 for 项 in 可迭代对象 if 条件]。例如 [x*x for x in range(10) if x%2==0] 得到偶数的平方。相比等价 for 循环，它更短且通常更快。但应避免写过长的推导式——当逻辑复杂时，可读性优先，改用常规循环。字典推导与集合推导语法类似。"}));
        map.put("CSS学习", List.of(
                new String[]{"Flexbox 布局核心",
                        "Flexbox 是一维布局模型。容器设 display:flex 后，子项沿主轴排列（flex-direction: row 横向 / column 纵向）。justify-content 控制主轴对齐（flex-start/center/space-between 等），align-items 控制交叉轴对齐。flex: 1 让子项按比例伸缩。flex-wrap: wrap 允许换行。它特别适合导航栏、卡片排列、垂直居中等场景，比浮动布局更直观可控。"},
                new String[]{"CSS 自定义属性（变量）",
                        "CSS 变量（自定义属性）以 -- 开头定义，如 --brand: #4f46e5，通过 var(--brand) 使用。:root 中定义即为全局变量。变量可继承，子元素可覆盖父级定义，从而实现主题切换：切换 class 即可整体换色。变量还能结合 calc() 做计算。相比预处理器变量，原生变量在运行时可被 JavaScript 动态修改，适合暗色模式与动态主题。"}));
        map.put("Vue学习", List.of(
                new String[]{"Vue3 组合式 API",
                        "Vue3 推荐组合式 API：在 script setup 中直接编写。ref 用于基本类型响应式（访问需 .value），reactive 用于对象。computed 派生状态，watch/watchEffect 侦听变化。生命周期钩子如 onMounted 在 setup 中直接调用。组合式 API 将同一逻辑的状态与函数聚合在一起，相比选项式 API 更利于逻辑复用与 TypeScript 支持。"},
                new String[]{"响应式原理",
                        "Vue3 使用 Proxy 实现响应式：对对象进行代理，读取时通过 track 收集依赖（哪些 effect 用到该属性），修改时通过 trigger 通知依赖重新执行。相比 Vue2 的 Object.defineProperty，Proxy 能监听属性新增/删除与数组索引变化，无需 $set。深层对象通过递归代理实现。响应式系统让数据变化自动驱动视图更新，是框架渲染的核心机制。"}));
        map.put("大模型学习", List.of(
                new String[]{"Transformer 注意力机制",
                        "Transformer 以自注意力机制为核心。每个 token 映射为查询 Q、键 K、值 V 三个向量；注意力分数由 Q 与所有 K 的点积经缩放 softmax 得到，再对 V 加权求和，使模型关注上下文中最相关的词。多头注意力并行计算多组 Q/K/V，捕捉不同子空间关系。相比 RNN，注意力可并行计算且能直接建模远距离依赖，是现代大模型的基础。"},
                new String[]{"Prompt 工程基础",
                        "Prompt 工程通过设计输入引导大模型产出期望结果。基本结构包含：角色（如『你是一名资深工程师』）、任务（清晰描述要做什么）、上下文（必要背景与约束）、示例（少样本提升稳定性）、输出格式（JSON/列表等）。技巧：指令明确具体、给步骤、限定范围、要求逐步推理（CoT）。好的 Prompt 应可复用、可评测，并随模型迭代持续优化。"}));
        return map;
    }
}
