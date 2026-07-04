# BankerAlgorithm

基于 Java Swing 的银行家算法演示程序，用于展示操作系统中的死锁避免过程。

程序支持初始化资源环境、编辑 Max 和 Allocation 矩阵、自动计算 Need 与 Available、执行安全性检测、处理单个进程的资源请求，并提供示例数据快速体验。

## 功能

- 图形化 Swing 界面
- 银行家算法安全性检测
- 资源请求试探分配与回滚判断
- 示例数据一键载入
- 根据 Allocation 自动刷新 Available
- 中文界面与中英混合运行日志

## 项目结构

```text
src/
  banker/
    BankerAlgorithm.java
```

## 环境要求

- JDK 11 或更高版本

## 运行方式

在仓库根目录执行：

```bash
javac -encoding UTF-8 -d out src/banker/BankerAlgorithm.java
java -cp out banker.BankerAlgorithm
```

也可以在 IntelliJ IDEA、Eclipse 等 IDE 中打开项目，并运行主类 `banker.BankerAlgorithm`。

## 使用流程

1. 设置进程数、资源种类数和资源总数，点击“初始化”。
2. 在 Max 和 Allocation 表格中填写最大需求与已分配资源。
3. 点击“安全性检测”查看系统是否安全。
4. 在 Request 区域填写进程号和请求向量，点击“提交请求”。
5. 程序会判断请求是否超出 Need、是否超出 Available，以及试探分配后是否仍处于安全状态。

## 说明

资源总数会平均分配到各资源类型；当不能整除时，余数依次分配给靠前的资源类型。例如资源总数为 29、资源种类为 3 时，各类资源总量为 `[10, 10, 9]`。
