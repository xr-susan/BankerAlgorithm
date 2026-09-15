package banker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BankerSolver} 的单元测试。
 *
 * <p>除少量用于锁定贪心策略的确定性断言外，多数用例都通过
 * {@link #assertSequenceIsValid} 独立复算安全序列，避免测试只是复述实现。
 */
class BankerSolverTest {

    /** 教材经典例子：5 个进程、3 类资源，资源总量 A=10, B=5, C=7。 */
    private static final int[] CLASSIC_TOTALS = {10, 5, 7};
    private static final int[][] CLASSIC_MAX = {
            {7, 5, 3},
            {3, 2, 2},
            {9, 0, 2},
            {2, 2, 2},
            {4, 3, 3},
    };
    private static final int[][] CLASSIC_ALLOCATION = {
            {0, 1, 0},
            {2, 0, 0},
            {3, 0, 2},
            {2, 1, 1},
            {0, 0, 2},
    };
    private static final int[][] CLASSIC_NEED = {
            {7, 4, 3},
            {1, 2, 2},
            {6, 0, 0},
            {0, 1, 1},
            {4, 3, 1},
    };

    /** 独立复算：按给定顺序依次分配，检查每一步的 Need 都能被当时的 Work 满足。 */
    private static void assertSequenceIsValid(List<Integer> sequence, int[] available,
                                              int[][] allocation, int[][] need) {
        assertEquals(allocation.length, sequence.size(), "安全序列应包含全部进程");
        assertEquals(allocation.length, sequence.stream().distinct().count(), "安全序列不应有重复进程");

        int[] work = available.clone();
        for (int process : sequence) {
            assertTrue(BankerSolver.canSatisfy(need[process], work),
                    "P" + process + " 在 Work=" + BankerSolver.format(work) + " 时无法完成");
            for (int j = 0; j < work.length; j++) {
                work[j] += allocation[process][j];
            }
        }
    }

    @Nested
    @DisplayName("派生量计算")
    class DerivedQuantities {

        @Test
        @DisplayName("Need = Max - Allocation")
        void computesNeedFromMaxAndAllocation() {
            int[] flat = BankerSolver.computeNeed(CLASSIC_MAX, CLASSIC_ALLOCATION);
            assertArrayEquals(new int[]{
                    7, 4, 3,
                    1, 2, 2,
                    6, 0, 0,
                    0, 1, 1,
                    4, 3, 1,
            }, flat);
        }

        @Test
        @DisplayName("已分配量超过最大需求时抛出异常")
        void rejectsAllocationAboveMax() {
            int[][] max = {{1, 1}};
            int[][] allocation = {{2, 0}};
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> BankerSolver.computeNeed(max, allocation));
            assertTrue(error.getMessage().contains("P0"), "异常信息应指出违规进程");
        }

        @Test
        @DisplayName("Available = 总量 - 已分配之和")
        void computesAvailableFromTotals() {
            assertArrayEquals(new int[]{3, 3, 2},
                    BankerSolver.computeAvailable(CLASSIC_TOTALS, CLASSIC_ALLOCATION));
        }

        @Test
        @DisplayName("已分配总量超过资源总量时抛出异常")
        void rejectsOverAllocatedResource() {
            int[][] allocation = {{3, 0}, {3, 0}};
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> BankerSolver.computeAvailable(new int[]{5, 5}, allocation));
            assertTrue(error.getMessage().contains("R0"));
        }

        @Test
        @DisplayName("资源总量列数不匹配时抛出异常")
        void rejectsMismatchedTotalsLength() {
            assertThrows(IllegalArgumentException.class,
                    () -> BankerSolver.computeAvailable(new int[]{5}, CLASSIC_ALLOCATION));
        }
    }

    @Nested
    @DisplayName("安全性检测")
    class SafetyCheck {

        @Test
        @DisplayName("经典例子：系统安全，且序列可独立复算通过")
        void classicExampleIsSafe() {
            BankerSolver.SafetyReport report =
                    BankerSolver.checkSafety(new int[]{3, 3, 2}, CLASSIC_ALLOCATION, CLASSIC_NEED);

            assertTrue(report.safe());
            assertEquals(List.of(1, 3, 0, 2, 4), report.sequence(),
                    "贪心策略应从最小进程号重新扫描，得到确定的序列");
            assertSequenceIsValid(report.sequence(), new int[]{3, 3, 2}, CLASSIC_ALLOCATION, CLASSIC_NEED);
        }

        @Test
        @DisplayName("每一步都记录了 Work 的变化，且前后状态自洽")
        void stepsRecordWorkProgression() {
            BankerSolver.SafetyReport report =
                    BankerSolver.checkSafety(new int[]{3, 3, 2}, CLASSIC_ALLOCATION, CLASSIC_NEED);

            assertEquals(report.sequence().size(), report.steps().size());
            assertArrayEquals(new int[]{3, 3, 2}, report.steps().get(0).workBefore(),
                    "第一步之前的 Work 应等于初始 Available");

            for (int index = 0; index < report.steps().size(); index++) {
                BankerSolver.Step step = report.steps().get(index);
                assertEquals(index + 1, step.order());
                assertEquals(report.sequence().get(index), step.process());

                int[] expectedBefore = index == 0
                        ? new int[]{3, 3, 2}
                        : report.steps().get(index - 1).workAfter();
                assertArrayEquals(expectedBefore, step.workBefore(),
                        "第 " + (index + 1) + " 步的 Work 应接续上一步的结果");

                for (int j = 0; j < step.workBefore().length; j++) {
                    assertEquals(step.workBefore()[j] + CLASSIC_ALLOCATION[step.process()][j],
                            step.workAfter()[j], "Work 的增量应等于该进程的已分配量");
                }
            }
        }

        @Test
        @DisplayName("互不相让的死锁局面判定为不安全")
        void detectsUnsafeState() {
            int[][] allocation = {{1, 1}, {1, 1}};
            int[][] need = {{1, 0}, {0, 1}};

            BankerSolver.SafetyReport report =
                    BankerSolver.checkSafety(new int[]{0, 0}, allocation, need);

            assertFalse(report.safe());
            assertTrue(report.sequence().isEmpty(), "不安全时不应给出安全序列");
            assertTrue(report.steps().isEmpty(), "一步都无法推进");
        }

        @Test
        @DisplayName("空系统视为安全")
        void emptySystemIsSafe() {
            BankerSolver.SafetyReport report =
                    BankerSolver.checkSafety(new int[]{0, 0}, new int[0][], new int[0][]);

            assertTrue(report.safe());
            assertTrue(report.sequence().isEmpty());
        }

        @Test
        @DisplayName("单进程只要需求可满足就是安全的")
        void singleProcessIsSafe() {
            BankerSolver.SafetyReport report =
                    BankerSolver.checkSafety(new int[]{1, 1}, new int[][]{{1, 0}}, new int[][]{{0, 1}});

            assertTrue(report.safe());
            assertEquals(List.of(0), report.sequence());
        }

        @Test
        @DisplayName("矩阵形状不一致时抛出异常")
        void rejectsMismatchedShapes() {
            assertThrows(IllegalArgumentException.class,
                    () -> BankerSolver.checkSafety(new int[]{3, 3, 2},
                            CLASSIC_ALLOCATION, new int[][]{{1, 2, 3}}));
        }

        @Test
        @DisplayName("可用资源向量长度与矩阵列数不一致时抛出异常")
        void rejectsMismatchedAvailableLength() {
            assertThrows(IllegalArgumentException.class,
                    () -> BankerSolver.checkSafety(new int[]{3, 3},
                            CLASSIC_ALLOCATION, CLASSIC_NEED));
        }
    }

    @Nested
    @DisplayName("canSatisfy")
    class CanSatisfy {

        @Test
        @DisplayName("逐项小于等于即满足")
        void satisfiedWhenEveryEntryFits() {
            assertTrue(BankerSolver.canSatisfy(new int[]{1, 2, 2}, new int[]{3, 3, 2}));
        }

        @Test
        @DisplayName("只要有一项超出就不满足")
        void unsatisfiedWhenAnyEntryExceeds() {
            assertFalse(BankerSolver.canSatisfy(new int[]{7, 4, 3}, new int[]{3, 3, 2}));
        }

        @Test
        @DisplayName("长度不一致时抛出异常")
        void rejectsMismatchedLength() {
            assertThrows(IllegalArgumentException.class,
                    () -> BankerSolver.canSatisfy(new int[]{1, 2}, new int[]{1}));
        }
    }

    @Nested
    @DisplayName("资源请求处理")
    class RequestHandling {

        @Test
        @DisplayName("P1 请求 (1,0,2)：允许分配并给出新状态")
        void grantsRequestThatKeepsSystemSafe() {
            BankerSolver.RequestReport report = BankerSolver.evaluateRequest(
                    new int[]{3, 3, 2}, CLASSIC_ALLOCATION, CLASSIC_NEED, 1, new int[]{1, 0, 2});

            assertEquals(BankerSolver.RequestStatus.GRANTED, report.status());
            assertArrayEquals(new int[]{2, 3, 0}, report.available(), "Available 应扣减请求量");
            assertArrayEquals(new int[]{3, 0, 2}, report.allocation()[1], "P1 的已分配量应增加");
            assertArrayEquals(new int[]{0, 2, 0}, report.need()[1], "P1 的 Need 应减少");
            assertNotNull(report.safety());
            assertSequenceIsValid(report.safety().sequence(), report.available(),
                    report.allocation(), report.need());
        }

        @Test
        @DisplayName("请求量超过 Need 时拒绝，并指出违规资源")
        void rejectsRequestExceedingNeed() {
            BankerSolver.RequestReport report = BankerSolver.evaluateRequest(
                    new int[]{3, 3, 2}, CLASSIC_ALLOCATION, CLASSIC_NEED, 1, new int[]{2, 0, 0});

            assertEquals(BankerSolver.RequestStatus.EXCEEDS_NEED, report.status());
            assertEquals(0, report.violatingResource());
            assertArrayEquals(new int[]{3, 3, 2}, report.available(), "被拒绝时不应改动状态");
        }

        @Test
        @DisplayName("可用资源不足时进入等待，并指出缺口资源")
        void reportsInsufficientResources() {
            BankerSolver.RequestReport report = BankerSolver.evaluateRequest(
                    new int[]{3, 3, 2}, CLASSIC_ALLOCATION, CLASSIC_NEED, 0, new int[]{7, 4, 3});

            assertEquals(BankerSolver.RequestStatus.INSUFFICIENT_RESOURCES, report.status());
            assertEquals(0, report.violatingResource());
            assertArrayEquals(new int[]{3, 3, 2}, report.available());
        }

        @Test
        @DisplayName("试探分配后系统不安全则拒绝，且不改变原状态")
        void rejectsRequestThatWouldMakeSystemUnsafe() {
            int[] available = {3, 3, 2};
            int[][] allocationBefore = BankerSolver.copyMatrix(CLASSIC_ALLOCATION);

            BankerSolver.RequestReport report = BankerSolver.evaluateRequest(
                    available, CLASSIC_ALLOCATION, CLASSIC_NEED, 4, new int[]{3, 3, 0});

            assertEquals(BankerSolver.RequestStatus.UNSAFE, report.status());
            assertNotNull(report.safety());
            assertFalse(report.safety().safe());
            assertArrayEquals(available, report.available(), "拒绝后 Available 应保持原值");
            assertArrayEquals(allocationBefore[4], report.allocation()[4], "拒绝后 Allocation 应保持原值");
        }

        @Test
        @DisplayName("零请求总是被接受")
        void grantsZeroRequest() {
            BankerSolver.RequestReport report = BankerSolver.evaluateRequest(
                    new int[]{3, 3, 2}, CLASSIC_ALLOCATION, CLASSIC_NEED, 0, new int[]{0, 0, 0});

            assertEquals(BankerSolver.RequestStatus.GRANTED, report.status());
            assertArrayEquals(new int[]{3, 3, 2}, report.available());
        }

        @Test
        @DisplayName("进程号越界时抛出异常")
        void rejectsOutOfRangeProcess() {
            assertThrows(IllegalArgumentException.class, () -> BankerSolver.evaluateRequest(
                    new int[]{3, 3, 2}, CLASSIC_ALLOCATION, CLASSIC_NEED, 5, new int[]{0, 0, 0}));
            assertThrows(IllegalArgumentException.class, () -> BankerSolver.evaluateRequest(
                    new int[]{3, 3, 2}, CLASSIC_ALLOCATION, CLASSIC_NEED, -1, new int[]{0, 0, 0}));
        }

        @Test
        @DisplayName("请求向量长度不匹配或含负数时抛出异常")
        void rejectsMalformedRequest() {
            assertThrows(IllegalArgumentException.class, () -> BankerSolver.evaluateRequest(
                    new int[]{3, 3, 2}, CLASSIC_ALLOCATION, CLASSIC_NEED, 0, new int[]{0, 0}));
            assertThrows(IllegalArgumentException.class, () -> BankerSolver.evaluateRequest(
                    new int[]{3, 3, 2}, CLASSIC_ALLOCATION, CLASSIC_NEED, 0, new int[]{-1, 0, 0}));
        }

        @Test
        @DisplayName("连续两次请求可让 P1 的 Need 归零，且中间状态始终安全")
        void successiveGrantsDrainOneProcess() {
            int[] available = {3, 3, 2};
            int[][] allocation = BankerSolver.copyMatrix(CLASSIC_ALLOCATION);
            int[][] need = BankerSolver.copyMatrix(CLASSIC_NEED);

            BankerSolver.RequestReport first = BankerSolver.evaluateRequest(
                    available, allocation, need, 1, new int[]{1, 0, 2});
            assertEquals(BankerSolver.RequestStatus.GRANTED, first.status());
            assertArrayEquals(new int[]{0, 2, 0}, first.need()[1]);

            BankerSolver.RequestReport second = BankerSolver.evaluateRequest(
                    first.available(), first.allocation(), first.need(), 1, new int[]{0, 2, 0});
            assertEquals(BankerSolver.RequestStatus.GRANTED, second.status(),
                    "P1 剩余的 Need 应仍可满足");
            assertArrayEquals(new int[]{0, 0, 0}, second.need()[1], "P1 的 Need 应归零");
            assertArrayEquals(new int[]{3, 2, 2}, second.allocation()[1]);
            assertArrayEquals(new int[]{2, 1, 0}, second.available());
            assertSequenceIsValid(second.safety().sequence(), second.available(),
                    second.allocation(), second.need());
        }
    }

    @Nested
    @DisplayName("工具方法")
    class Utilities {

        @Test
        @DisplayName("copyMatrix 返回独立的深拷贝")
        void copyMatrixIsDeep() {
            int[][] source = {{1, 2}, {3, 4}};
            int[][] copy = BankerSolver.copyMatrix(source);

            copy[0][0] = 99;
            assertEquals(1, source[0][0], "修改副本不应影响原矩阵");
        }

        @Test
        @DisplayName("reshape 能还原矩阵")
        void reshapeRebuildsMatrix() {
            int[][] matrix = BankerSolver.reshape(new int[]{1, 2, 3, 4, 5, 6}, 2, 3);
            assertArrayEquals(new int[]{1, 2, 3}, matrix[0]);
            assertArrayEquals(new int[]{4, 5, 6}, matrix[1]);
        }

        @Test
        @DisplayName("isRectangular 识别非矩形矩阵，空矩阵视为合法")
        void detectsNonRectangularMatrix() {
            assertTrue(BankerSolver.isRectangular(new int[][]{{1, 2}, {3, 4}}));
            assertFalse(BankerSolver.isRectangular(new int[][]{{1, 2}, {3}}));
            assertFalse(BankerSolver.isRectangular(null));
            assertTrue(BankerSolver.isRectangular(new int[0][]), "0 个进程是合法输入");
        }

        @Test
        @DisplayName("空矩阵下的派生量计算不抛异常")
        void derivedQuantitiesTolerateEmptySystem() {
            assertArrayEquals(new int[0], BankerSolver.computeNeed(new int[0][], new int[0][]));
            assertArrayEquals(new int[]{4, 5}, BankerSolver.computeAvailable(new int[]{4, 5}, new int[0][]));
        }

        @Test
        @DisplayName("format 输出可读向量")
        void formatsVector() {
            assertEquals("[1, 2, 3]", BankerSolver.format(new int[]{1, 2, 3}));
        }

        @Test
        @DisplayName("工具类不可实例化")
        void cannotInstantiate() throws Exception {
            var constructor = BankerSolver.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
        }

        @Test
        @DisplayName("返回的数组是防御性拷贝")
        void reportArraysAreDefensiveCopies() {
            BankerSolver.SafetyReport report =
                    BankerSolver.checkSafety(new int[]{3, 3, 2}, CLASSIC_ALLOCATION, CLASSIC_NEED);
            int[] first = report.steps().get(0).need();
            first[0] = 12345;
            assertArrayEquals(CLASSIC_NEED[1], report.steps().get(0).need(), "外部修改不应污染内部状态");
        }

        @Test
        @DisplayName("sequence 不可变")
        void sequenceIsImmutable() {
            BankerSolver.SafetyReport report =
                    BankerSolver.checkSafety(new int[]{3, 3, 2}, CLASSIC_ALLOCATION, CLASSIC_NEED);
            assertThrows(UnsupportedOperationException.class, () -> report.sequence().add(9));
        }

        @Test
        @DisplayName("UNSAFE 报告保留原状态，并带上失败的安全性分析")
        void unsafeReportKeepsOriginalState() {
            BankerSolver.RequestReport report = BankerSolver.evaluateRequest(
                    new int[]{3, 3, 2}, CLASSIC_ALLOCATION, CLASSIC_NEED, 4, new int[]{3, 3, 0});

            assertNotNull(report.safety(), "被拒绝的请求也应返回安全性分析，便于界面解释原因");
            assertFalse(report.safety().safe());
            assertArrayEquals(new int[]{3, 3, 2}, report.available());
            assertArrayEquals(CLASSIC_ALLOCATION[4], report.allocation()[4]);
            assertArrayEquals(CLASSIC_NEED[4], report.need()[4]);
            assertNull(report.safety().sequence().isEmpty() ? null : report.safety().sequence());
        }
    }
}
