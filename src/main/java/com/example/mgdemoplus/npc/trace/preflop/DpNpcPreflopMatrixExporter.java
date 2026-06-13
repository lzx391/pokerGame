package com.example.mgdemoplus.npc.trace.preflop;

import com.example.mgdemoplus.npc.eval.DpNpcPreflopHandGrouper.HoleInfo;
import com.example.mgdemoplus.npc.strategypro.preflop.DpNpcUnifiedPreflopStrategy.DecisionMatrixExport;
import com.example.mgdemoplus.npc.trace.model.DpNpcPreflopMatrixSnapshot;

/** 薄封装：将 {@link DpNpcUnifiedPreflopStrategy} 导出的 matrix 转为 trace 快照。 */
public final class DpNpcPreflopMatrixExporter {
    private static final String[] LABELS = {"A", "K", "Q", "J", "T", "9", "8", "7", "6", "5", "4", "3", "2"};

    private DpNpcPreflopMatrixExporter() {
    }

    public static DpNpcPreflopMatrixSnapshot fromExport(DecisionMatrixExport exp) {
        if (exp == null || exp.matrix == null) {
            return null;
        }
        DpNpcPreflopMatrixSnapshot snap = new DpNpcPreflopMatrixSnapshot();
        snap.labels = LABELS.clone();
        snap.cells = toDisplayGrid(exp.matrix);
        snap.matrixKind = exp.matrixKind;
        snap.spot = exp.spot;
        snap.position = exp.position != null ? exp.position.name() : "";
        snap.rangeLevel = exp.rangeLevel;
        snap.heroHandLabel = DpNpcPreflopMatrixSnapshotHero.label(exp.hole);
        DpNpcPreflopMatrixSnapshot.HeroCell hero = heroCell(exp.matrix, exp.hole);
        snap.heroCell = hero;
        return snap;
    }

    /** 策略内 matrix 为 2-first 存储坐标；导出为与 labels 一致的 A-first 13×13 显示坐标。 */
    private static int[][] toDisplayGrid(byte[][] matrix) {
        int n = matrix.length;
        int[][] out = new int[n][];
        for (int sRow = 0; sRow < n; sRow++) {
            out[sRow] = new int[matrix[sRow].length];
        }
        for (int sRow = 0; sRow < n; sRow++) {
            for (int sCol = 0; sCol < matrix[sRow].length; sCol++) {
                int dRow = storageToDisplayIndex(sCol);
                int dCol = storageToDisplayIndex(sRow);
                out[dRow][dCol] = matrix[sRow][sCol] & 0xFF;
            }
        }
        return out;
    }

    static DpNpcPreflopMatrixSnapshot.HeroCell heroCell(byte[][] matrix, HoleInfo hole) {
        if (hole == null || !hole.valid || matrix == null) {
            return new DpNpcPreflopMatrixSnapshot.HeroCell(0, 0, false);
        }
        int ia = rankToStorageIndex(hole.r1);
        int ib = rankToStorageIndex(hole.r2);
        int hi = Math.max(ia, ib);
        int lo = Math.min(ia, ib);
        int storageRow;
        int storageCol;
        if (hi == lo) {
            storageRow = hi;
            storageCol = hi;
        } else if (hole.suited) {
            storageRow = lo;
            storageCol = hi;
        } else {
            storageRow = hi;
            storageCol = lo;
        }
        boolean inRange = storageRow >= 0 && storageRow < matrix.length
                && storageCol >= 0 && storageCol < matrix[storageRow].length
                && matrix[storageRow][storageCol] != 0;
        int displayRow = storageToDisplayIndex(storageCol);
        int displayCol = storageToDisplayIndex(storageRow);
        return new DpNpcPreflopMatrixSnapshot.HeroCell(displayRow, displayCol, inRange);
    }

    private static int storageToDisplayIndex(int storageIndex) {
        return 12 - storageIndex;
    }

    private static int rankToStorageIndex(int rank) {
        if (rank < 2 || rank > 14) {
            return -1;
        }
        return rank - 2;
    }

    /** 避免 trace 包依赖 preflop 策略内的 package-private label 逻辑。 */
    private static final class DpNpcPreflopMatrixSnapshotHero {
        private static String label(HoleInfo hole) {
            if (hole == null || !hole.valid) {
                return "";
            }
            int hi = Math.max(hole.r1, hole.r2);
            int lo = Math.min(hole.r1, hole.r2);
            String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "T", "J", "Q", "K", "A"};
            String rHi = ranks[hi - 2];
            String rLo = ranks[lo - 2];
            if (hi == lo) {
                return rHi + rLo;
            }
            return rHi + rLo + (hole.suited ? "s" : "o");
        }
    }
}
