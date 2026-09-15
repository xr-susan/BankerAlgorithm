# BankerAlgorithm

[![CI](https://github.com/xr-susan/BankerAlgorithm/actions/workflows/ci.yml/badge.svg)](https://github.com/xr-susan/BankerAlgorithm/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](pom.xml)

基于 Java Swing 的银行家算法（Banker's Algorithm）演示程序，用于直观展示操作系统中的死锁避免过程。

算法核心与图形界面**完全解耦**：`BankerSolver` 是一个不依赖任何 Swing 组件的纯计算类，配有 32 个 JUnit 5 单元测试；`BankerAlgorithm` 只负责收集输入、调用核心算法、把结果渲染成表格与日志。

## 功能特性

| 功能 | 说明 |
| --- | --- |
| 图形化界面 | Swing 实现的明亮主题界面，卡片式布局，中文字体自动探测与回退 |
| 参数初始化 | 可设置进程数、资源种类数、资源总量，资源总量按种类平均分配（余数依次补给靠前的资源） |
| 矩阵编辑 | 直接编辑 Max 与 Allocation 矩阵，Need 与 Available 自动推导刷新 |
| 安全性检测 | 执行经典安全性算法，输出逐步分配过程与一个安全序列 |
| 资源请求处理 | 按「是否超出 Need → 是否超出 Available → 试探分配后是否仍安全」三级判断处理请求 |
| 请求回滚 | 请求被拒绝时**不会**改动任何状态，界面数据与内部状态保持一致 |
| 示例数据 | 一键载入教材经典示例（2 进程 / 3 资源 / 总量 29） |
| 运行日志 | 带对齐格式的过程日志，展示每一步的 Need 与 Work 变化 |

## 算法说明

**需求矩阵与可用资源**

```
Need[i][j]   = Max[i][j] - Allocation[i][j]
Available[j] = ResourceTotal[j] - Σ Allocation[i][j]
```

**安全性检测**采用经典的贪心策略：从进程号最小的开始扫描，找到第一个 `Need[i] ≤ Work` 的进程让它完成并回收其资源（`Work += Allocation[i]`），然后**从头重新扫描**。若一轮扫描中没有任何进程可以推进，则系统处于不安全状态。

> 该策略得到的安全序列是确定的，但不唯一。测试中除了断言这个确定序列，还会用独立的复算函数验证序列本身确实安全。

**资源请求**依次经过三级判断：

1. `Request > Need` → 拒绝（`EXCEEDS_NEED`），并指出是哪个资源越界
2. `Request > Available` → 进程等待（`INSUFFICIENT_RESOURCES`）
3. 试探分配后重新做安全性检测，若不安全 → 拒绝（`UNSAFE`）

只有三级全部通过才会真正写回状态。

## 界面预览

程序界面需要在有图形环境的机器上运行才能截图，仓库暂未包含截图。可参考下表自行补充到 `docs/screenshots/`：

| 建议文件名 | 截图内容 |
| --- | --- |
| `docs/screenshots/main-window.png` | 主界面全貌（参数面板 + 三个矩阵表 + 输出区） |
| `docs/screenshots/safe-sequence.png` | 安全性检测通过后的安全序列输出 |
| `docs/screenshots/request-rejected.png` | 资源请求被拒绝时的提示与日志 |

## 项目结构

```text
BankerAlgorithm/
├── pom.xml                                     Maven 构建配置
├── src/main/java/banker/
│   ├── BankerSolver.java                       算法核心：纯计算，无 UI 依赖
│   └── BankerAlgorithm.java                    Swing 界面：输入收集与结果渲染
├── src/test/java/banker/
│   └── BankerSolverTest.java                   32 个 JUnit 5 单元测试
└── .github/workflows/ci.yml                    CI：JDK 17 / 21 双版本构建与测试
```

## 环境要求

- JDK 17 或更高版本（`pom.xml` 中 `maven.compiler.release` 设为 17）
- Maven 3.8+（使用 Maven 构建时）
- 图形环境（运行界面时；单元测试不需要）

## 构建与运行

### 使用 Maven（推荐）

```bash
mvn test                    # 运行单元测试
mvn package                 # 打包成可执行 jar
java -jar target/banker-algorithm.jar
```

### 不使用 Maven

只依赖 JDK，手工编译：

```bash
javac -encoding UTF-8 -d out src/main/java/banker/*.java
java -cp out banker.BankerAlgorithm
```

> `-encoding UTF-8` 不能省略：源码包含中文注释与中文字符串，且仓库统一使用 UTF-8 编码。

也可以在 IntelliJ IDEA / Eclipse 中直接打开为 Maven 项目，运行主类 `banker.BankerAlgorithm`。

## 测试

```bash
mvn test
```

测试全部集中在 `BankerSolverTest`，按主题分组：

| 分组 | 用例数 | 覆盖内容 |
| --- | --- | --- |
| 派生量计算 | 5 | Need / Available 推导，以及分配量超限、总量列数不匹配等异常路径 |
| 安全性检测 | 7 | 教材经典例子、死锁局面、单进程、空系统、矩阵形状校验 |
| canSatisfy | 3 | 逐项比较的边界行为 |
| 资源请求处理 | 8 | 四种 `RequestStatus` 全覆盖、零请求、连续请求、非法进程号与畸形请求 |
| 工具方法 | 9 | 深拷贝独立性、防御性拷贝、集合不可变性、矩阵形状判定 |

由于算法核心不引用任何 AWT/Swing 类，测试可以在无图形环境（包括 CI）中直接运行。

## 使用流程

1. 设置进程数、资源种类数和资源总数，点击「初始化」。
2. 在 Max 和 Allocation 表格中填写最大需求与已分配资源。
3. 点击「安全性检测」，在输出区查看系统是否安全及对应的安全序列。
4. 在 Request 区域填写进程号和请求向量，点击「提交请求」。
5. 程序会依次判断是否超出 Need、是否超出 Available，以及试探分配后是否仍处于安全状态。

**资源总量分配规则**：资源总数会平均分配到各资源类型；当不能整除时，余数依次分配给靠前的资源类型。例如资源总数为 29、资源种类为 3 时，各类资源总量为 `[10, 10, 9]`。

## 设计说明

- **算法与界面分离**：`BankerSolver` 不持有任何界面状态，输入输出都是普通数组与不可变的结果对象，因此可以脱离 Swing 单独测试，也便于将来接入 Web 或命令行前端。
- **结果对象代替返回值约定**：安全性检测返回 `SafetyReport`（含 `safe`、`sequence`、`steps`），资源请求返回 `RequestReport`（含 `status`、违规资源下标，以及仅在成功时生效的新状态）。调用方可以无条件采用报告中的状态，不会出现「拒绝后数据被改坏」的情况。
- **防御性拷贝**：所有对外暴露的数组都是拷贝，`sequence()` 返回不可变列表，避免外部代码意外修改内部状态。
- **输入校验集中在一处**：矩阵形状、负数、越界下标都由核心类统一校验并抛出带中文说明的 `IllegalArgumentException`，界面只负责把异常信息弹给用户。

## 已知限制

- 界面截图缺失（见上文「界面预览」）。
- 资源请求的判定结果是「一次试探」的结论，界面不会自动重试；需要用户手动调整请求量。
- 未实现进程主动释放资源的操作，进程完成后资源只体现在安全性检测的 Work 推演中。

## 路线图

- [ ] 补充界面截图与一段演示 GIF。
- [ ] 增加「进程完成并释放资源」的交互操作。
- [ ] 支持把当前矩阵导出为 CSV，便于写实验报告。
- [ ] 增加多组预设数据，方便课堂演示对比。

## License

[MIT](LICENSE)
