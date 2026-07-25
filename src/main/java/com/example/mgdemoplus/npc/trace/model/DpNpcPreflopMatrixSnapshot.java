package com.example.mgdemoplus.npc.trace.model;

/** 翻前 13×13 range matrix 快照。 */
public class DpNpcPreflopMatrixSnapshot {
    public String[] labels;
    public int[][] cells;
    public HeroCell heroCell;
    public String matrixKind;
    public String spot;
    public String position;
    public int rangeLevel;
    public String heroHandLabel;

    public static final class HeroCell {
        public int row;
        public int col;
        public boolean inRange;

        public HeroCell() {
        }

        public HeroCell(int row, int col, boolean inRange) {
            this.row = row;
            this.col = col;
            this.inRange = inRange;
        }
    }
}
