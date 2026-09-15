package banker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 银行家算法（Banker's Algorithm）的核心实现。
 *
 * <p>本类刻意不依赖任何 Swing 组件，因此可以被单元测试直接覆盖。
 * 界面层 {@link BankerAlgorithm} 只负责收集输入、调用这里的方法、
 * 再把返回的结果渲染成日志和表格。
 *
 * <p>算法约定：
 * <ul>
 *   <li>{@code need[i][j] = max[i][j] - allocation[i][j]}</li>
 *   <li>{@code available[j] = resourceTotals[j] - Σ allocation[i][j]}</li>
 *   <li>安全性检测采用经典的「按进程号从小到大找第一个可满足进程」贪心策略，
 *       每完成一个进程就从头重新扫描。</li>
 * </ul>
 */
public final class BankerSolver {

    private BankerSolver() {
        throw new AssertionError("工具类不允许实例化");
    }

    /** 安全性检测过程中完成的一次分配。 */
    public static final class Step {
        private final int order;
        private final int process;
        private final int[] need;
        private final int[] workBefore;
        private final int[] workAfter;

        Step(int order, int process, int[] need, int[] workBefore, int[] workAfter) {
            this.order = order;
            this.process = process;
            this.need = need.clone();
            this.workBefore = workBefore.clone();
            this.workAfter = workAfter.clone();
        }

        public int order() {
            return order;
        }

        public int process() {
            return process;
        }

        public int[] need() {
            return need.clone();
        }

        public int[] workBefore() {
            return workBefore.clone();
        }

        public int[] workAfter() {
            return workAfter.clone();
        }
    }

    /** 安全性检测结果。 */
    public static final class SafetyReport {
        private final boolean safe;
        private final List<Integer> sequence;
        private final List<Step> steps;

        SafetyReport(boolean safe, List<Integer> sequence, List<Step> steps) {
            this.safe = safe;
            this.sequence = List.copyOf(sequence);
            this.steps = List.copyOf(steps);
        }

        /** 系统是否处于安全状态。 */
        public boolean safe() {
            return safe;
        }

        /** 安全序列；不安全时为空列表。 */
        public List<Integer> sequence() {
            return sequence;
        }

        /** 搜索过程中的每一步，用于界面展示。 */
        public List<Step> steps() {
            return steps;
        }
    }

    /** 资源请求的处理结果。 */
    public enum RequestStatus {
        /** 请求合法且试探分配后系统仍然安全。 */
        GRANTED,
        /** 请求量超过该进程声明的最大需求。 */
        EXCEEDS_NEED,
        /** 当前可用资源不足以满足请求，进程需要等待。 */
        INSUFFICIENT_RESOURCES,
        /** 试探分配后系统进入不安全状态，请求被拒绝。 */
        UNSAFE
    }

    /** 资源请求的完整处理报告。 */
    public static final class RequestReport {
        private final RequestStatus status;
        private final int process;
        private final int[] request;
        private final int violatingResource;
        private final int[] available;
        private final int[][] allocation;
        private final int[][] need;
        private final SafetyReport safety;

        RequestReport(RequestStatus status, int process, int[] request, int violatingResource,
                      int[] available, int[][] allocation, int[][] need, SafetyReport safety) {
            this.status = status;
            this.process = process;
            this.request = request.clone();
            this.violatingResource = violatingResource;
            this.available = available.clone();
            this.allocation = copyMatrix(allocation);
            this.need = copyMatrix(need);
            this.safety = safety;
        }

        public RequestStatus status() {
            return status;
        }

        public int process() {
            return process;
        }

        public int[] request() {
            return request.clone();
        }

        /** 触发拒绝的资源下标；无违规时为 -1。 */
        public int violatingResource() {
            return violatingResource;
        }

        /** 请求被接受后的可用资源；被拒绝时等于请求前的状态。 */
        public int[] available() {
            return available.clone();
        }

        public int[][] allocation() {
            return copyMatrix(allocation);
        }

        public int[][] need() {
            return copyMatrix(need);
        }

        /** 请求被接受时的安全性报告，其余情况为 {@code null}。 */
        public SafetyReport safety() {
            return safety;
        }
    }

    // ------------------------------------------------------------------
    // 输入派生量
    // ------------------------------------------------------------------

    /**
     * 由最大需求矩阵与已分配矩阵计算需求矩阵。
     *
     * @throws IllegalArgumentException 当已分配量超过最大需求，或矩阵形状不合法时
     */
    public static int[] computeNeed(int[][] max, int[][] allocation) {
        requireRectangular(max, "Max");
        requireSameShape(max, allocation, "Allocation");

        int processes = max.length;
        if (processes == 0) {
            return new int[0];
        }
        int resources = max[0].length;
        int[] need = new int[processes * resources];

        for (int i = 0; i < processes; i++) {
            for (int j = 0; j < resources; j++) {
                if (max[i][j] < 0 || allocation[i][j] < 0) {
                    throw new IllegalArgumentException("资源数量不能为负数。");
                }
                if (allocation[i][j] > max[i][j]) {
                    throw new IllegalArgumentException(
                            "P" + i + " 的已分配量 R" + j + " 超过最大需求。");
                }
                need[i * resources + j] = max[i][j] - allocation[i][j];
            }
        }
        return need;
    }

    /** 由资源总量与已分配矩阵计算当前可用资源。 */
    public static int[] computeAvailable(int[] resourceTotals, int[][] allocation) {
        requireRectangular(allocation, "Allocation");
        if (allocation.length == 0) {
            return resourceTotals.clone();
        }
        if (resourceTotals.length != allocation[0].length) {
            throw new IllegalArgumentException("资源总量与已分配矩阵的列数不一致。");
        }

        int processes = allocation.length;
        int resources = resourceTotals.length;
        int[] available = resourceTotals.clone();

        for (int j = 0; j < resources; j++) {
            for (int i = 0; i < processes; i++) {
                available[j] -= allocation[i][j];
            }
            if (available[j] < 0) {
                throw new IllegalArgumentException(
                        "资源 R" + j + " 的已分配总量超过该类资源总量。");
            }
        }
        return available;
    }

    // ------------------------------------------------------------------
    // 安全性检测
    // ------------------------------------------------------------------

    /**
     * 判断系统是否处于安全状态，并给出一个安全序列。
     *
     * @param available  当前可用资源向量
     * @param allocation 已分配矩阵
     * @param need       需求矩阵
     * @return 安全性报告；不安全时 {@link SafetyReport#safe()} 返回 {@code false}
     */
    public static SafetyReport checkSafety(int[] available, int[][] allocation, int[][] need) {
        requireRectangular(allocation, "Allocation");
        requireSameShape(allocation, need, "Need");

        int processes = allocation.length;
        int resources = available.length;
        if (processes > 0 && resources != allocation[0].length) {
            throw new IllegalArgumentException("可用资源向量与已分配矩阵的列数不一致。");
        }

        int[] work = available.clone();
        boolean[] finished = new boolean[processes];
        List<Integer> sequence = new ArrayList<>(processes);
        List<Step> steps = new ArrayList<>(processes);

        while (sequence.size() < processes) {
            boolean progressed = false;
            for (int i = 0; i < processes; i++) {
                if (finished[i] || !canSatisfy(need[i], work)) {
                    continue;
                }
                int[] before = work.clone();
                for (int j = 0; j < resources; j++) {
                    work[j] += allocation[i][j];
                }
                finished[i] = true;
                sequence.add(i);
                steps.add(new Step(sequence.size(), i, need[i], before, work));
                progressed = true;
                break;
            }
            if (!progressed) {
                return new SafetyReport(false, List.of(), steps);
            }
        }
        return new SafetyReport(true, sequence, steps);
    }

    /** 判断 {@code needRow} 的每一项是否都不超过 {@code work}。 */
    public static boolean canSatisfy(int[] needRow, int[] work) {
        if (needRow.length != work.length) {
            throw new IllegalArgumentException("需求向量与工作向量的长度不一致。");
        }
        for (int j = 0; j < needRow.length; j++) {
            if (needRow[j] > work[j]) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 资源请求
    // ------------------------------------------------------------------

    /**
     * 处理某个进程的资源请求：先做两次合法性检查，再试探分配并重新做安全性检测。
     *
     * <p>返回报告中的 {@code available / allocation / need} 只在
     * {@link RequestStatus#GRANTED} 时代表新状态；其余情况原样返回请求前的状态，
     * 调用方可以无条件采用，不会污染现有数据。
     *
     * @param process 发起请求的进程号，必须落在 {@code [0, 进程数)} 内
     * @param request 请求向量，长度必须等于资源种类数
     */
    public static RequestReport evaluateRequest(int[] available, int[][] allocation, int[][] need,
                                                int process, int[] request) {
        requireRectangular(allocation, "Allocation");
        requireSameShape(allocation, need, "Need");

        int processes = allocation.length;
        int resources = available.length;

        if (process < 0 || process >= processes) {
            throw new IllegalArgumentException(
                    "进程号超出范围 [0, " + (processes - 1) + "]。");
        }
        if (request.length != resources) {
            throw new IllegalArgumentException("请求向量长度与资源种类数不一致。");
        }
        for (int value : request) {
            if (value < 0) {
                throw new IllegalArgumentException("请求量不能为负数。");
            }
        }

        for (int j = 0; j < resources; j++) {
            if (request[j] > need[process][j]) {
                return new RequestReport(RequestStatus.EXCEEDS_NEED, process, request, j,
                        available, allocation, need, null);
            }
        }
        for (int j = 0; j < resources; j++) {
            if (request[j] > available[j]) {
                return new RequestReport(RequestStatus.INSUFFICIENT_RESOURCES, process, request, j,
                        available, allocation, need, null);
            }
        }

        int[] trialAvailable = available.clone();
        int[][] trialAllocation = copyMatrix(allocation);
        int[][] trialNeed = copyMatrix(need);
        for (int j = 0; j < resources; j++) {
            trialAvailable[j] -= request[j];
            trialAllocation[process][j] += request[j];
            trialNeed[process][j] -= request[j];
        }

        SafetyReport safety = checkSafety(trialAvailable, trialAllocation, trialNeed);
        if (!safety.safe()) {
            return new RequestReport(RequestStatus.UNSAFE, process, request, -1,
                    available, allocation, need, safety);
        }
        return new RequestReport(RequestStatus.GRANTED, process, request, -1,
                trialAvailable, trialAllocation, trialNeed, safety);
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    /** 深拷贝二维数组。 */
    public static int[][] copyMatrix(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }

    /** 把二维数组按行拉平，便于界面从扁平数组还原需求矩阵。 */
    public static int[][] reshape(int[] flat, int processes, int resources) {
        int[][] matrix = new int[processes][resources];
        for (int i = 0; i < processes; i++) {
            System.arraycopy(flat, i * resources, matrix[i], 0, resources);
        }
        return matrix;
    }

    /** 判断矩阵每一行是否等长。空矩阵（0 个进程）视为合法。 */
    public static boolean isRectangular(int[][] matrix) {
        if (matrix == null) {
            return false;
        }
        if (matrix.length == 0) {
            return true;
        }
        if (matrix[0] == null) {
            return false;
        }
        int width = matrix[0].length;
        for (int[] row : matrix) {
            if (row == null || row.length != width) {
                return false;
            }
        }
        return true;
    }

    /** 把向量渲染成 {@code [1, 2, 3]} 形式，便于日志输出与测试断言。 */
    public static String format(int[] vector) {
        return Arrays.toString(vector);
    }

    private static void requireRectangular(int[][] matrix, String name) {
        if (!isRectangular(matrix)) {
            throw new IllegalArgumentException(name + " 必须是每行等长的矩阵。");
        }
    }

    private static void requireSameShape(int[][] first, int[][] second, String secondName) {
        requireRectangular(second, secondName);
        if (first.length != second.length) {
            throw new IllegalArgumentException(secondName + " 与已分配矩阵的形状不一致。");
        }
        if (first.length == 0) {
            return;
        }
        if (first[0].length != second[0].length) {
            throw new IllegalArgumentException(secondName + " 与已分配矩阵的形状不一致。");
        }
    }
}
