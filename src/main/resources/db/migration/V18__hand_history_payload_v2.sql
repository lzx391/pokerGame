-- payload v2: actions.actorChipsAfter, boardsByStreet.potTotalAtStreetEnd, startingStackBb（payload + 主表冗余列）
ALTER TABLE dp_observed_hand_history
    ADD COLUMN starting_stack_bb INT NOT NULL DEFAULT 0
        COMMENT '建房带入倍数（BB）；payload v2+ 写入'
        AFTER big_blind_chips;
