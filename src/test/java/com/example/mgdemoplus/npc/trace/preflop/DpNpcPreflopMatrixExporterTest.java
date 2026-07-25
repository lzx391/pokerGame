package com.example.mgdemoplus.npc.trace.preflop;

import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.eval.DpNpcPreflopHandGrouper.HoleInfo;
import com.example.mgdemoplus.npc.strategypro.preflop.DpNpcUnifiedPreflopStrategy;
import com.example.mgdemoplus.npc.strategypro.preflop.DpNpcUnifiedPreflopStrategy.DecisionMatrixExport;
import com.example.mgdemoplus.npc.trace.model.DpNpcPreflopMatrixSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DpNpcPreflopMatrixExporterTest {

    private static final String[] LABELS = {"A", "K", "Q", "J", "T", "9", "8", "7", "6", "5", "4", "3", "2"};

    @Test
    void heroCell_matchesHeroHandLabel_for65oAkSAndAA() {
        assertHeroAligned("65o", new HoleInfo(true, 6, 5, false), 4, 3);
        assertHeroAligned("AKs", new HoleInfo(true, 14, 13, true), 11, 12);
        assertHeroAligned("AA", new HoleInfo(true, 14, 14, false), 12, 12);
    }

    private static void assertHeroAligned(String expectedLabel, HoleInfo hole, int storageRow, int storageCol) {
        byte[][] matrix = new byte[13][13];
        matrix[storageRow][storageCol] = 1;

        DecisionMatrixExport exp = DpNpcUnifiedPreflopStrategy.exportDecisionMatrix(
                matrix,
                "testMatrix",
                null,
                DpNpcEngine.TablePosition.EARLY,
                5,
                hole);

        DpNpcPreflopMatrixSnapshot snap = DpNpcPreflopMatrixExporter.fromExport(exp);
        assertNotNull(snap);
        assertEquals(expectedLabel, snap.heroHandLabel);

        int row = snap.heroCell.row;
        int col = snap.heroCell.col;
        String labelFromGrid = handLabelAt(row, col);
        assertEquals(expectedLabel, labelFromGrid);
        assertEquals(1, snap.cells[row][col]);
        assertEquals(true, snap.heroCell.inRange);
    }

    private static String handLabelAt(int row, int col) {
        String rHi = LABELS[Math.min(row, col)];
        String rLo = LABELS[Math.max(row, col)];
        if (row == col) {
            return rHi + rLo;
        }
        if (row < col) {
            return rHi + rLo + "s";
        }
        return rHi + rLo + "o";
    }
}
