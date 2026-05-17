# P0 涓撻」锛氭埧鍐呭績璺宠秴鏃?/ 绉婚櫎鍚嶅崟 涓?WebSocket 鏄惁涓€骞舵柇寮€

**鐩殑**锛氶槻姝㈡紡鍒ゃ€屼汉宸插湪鏈嶅姟绔粠鎴块棿/瑙備紬鍒楄〃绉婚櫎锛屼絾瀵瑰眬 WS 浠嶅彲鏀舵帹閫併€嶇被 bug銆傚洓涓?Agent锛堟埧闂寸敓鍛藉懆鏈?/ WS / 蹇尮绀句氦 / 瀵瑰眬 NPC锛夊潎闇€瀵圭収鏈枃锛涙暣鍚堢蹇呴』鎶婄粨璁哄苟鍏ユ€昏〃銆?
**璇昏€?*锛氫骇鍝佷笌瀹炵幇璇勫锛涗笉娑夊強鍏蜂綋淇鏂规鏃舵湰鏂囦粎璁板綍**鐜扮姸**锛屼慨澶嶅彟寮€浠诲姟銆?
---

## 1. 婧愮爜浜嬪疄锛堜笂妗岀湡浜?路 HTTP 蹇冭烦瓒呮椂锛?
鍦?`DpRoomServiceImpl.tickEvictStaleSeatedPlayersOnHeartbeat`锛?
- 绫绘敞閲婂啓鏄庯細**銆岄獙鐪熶汉锛屽績璺宠秴鏃剁Щ闄ゆ埧闂达紝娌℃柇 ws銆傘€?*
- **鏈簲**鍦ㄦ湰鏂规硶鍐呰皟鐢ㄧ殑 **`gameRoomPushService.shutdownSubscriptionsForNicknameInRoom(roomId, nick)`** 褰撳墠涓?**鏁磋娉ㄩ噴**锛屽洜姝?*涓嶄細鍦ㄧЩ闄ょ灛闂翠富鍔ㄥ叧鍚屽悕 `viewerNickname` 鐨?WS**锛岃锛?  ```276:301:src/main/java/com/example/mgdemoplus/service/serviceImpl/DpRoomServiceImpl.java
  /** 楠岀湡浜猴紝蹇冭烦瓒呮椂绉婚櫎鎴块棿锛屾病鏂瓀s銆?*/
  ...
          // gameRoomPushService.shutdownSubscriptionsForNicknameInRoom(room.getRoomId(), hbNick);
  ```

渚濊禆鐨?*寤跺悗鍏宠繛璺緞**鏄細**鍚屼竴绉掔殑 Timer 绋嶅悗**鑻ヨ兘鎵ц鍒? 
`broadcastRoomAndMaybeRefreshLobbyAfterHeartbeatTick` 鈫?`DpGameRoomPushService.broadcastIfSubscribed`锛屽垯鍦ㄥ箍鎾噷瀵规瘡涓?session 鏍￠獙 `roomService.isNicknameInRoom(...)`锛氳嫢鏄电О宸蹭笉鍦ㄦ埧鍐咃紝鍒欎笅鍙?`{"_ws":"roomClosed"}`銆佷粠璁㈤槄闆嗗悎绉婚櫎骞?`close(GOING_AWAY)`銆?
---

## 2. 宸茬煡鐨勩€屼笉骞挎挱 鈬?涓嶅叧 WS銆嶇獥鍙?
鍦ㄥ悓涓€ `runGlobalSecondTickForSingleRoom` 鍐咃紝`tickEvictStaleSeatedPlayersOnHeartbeat` **涔嬪悗**锛岃嫢 **`skipBroadcastForLoneSettledPlayerWaitingNothing(room)`** 涓?true锛屼細鐩存帴 **`return`**锛屼粠鑰?*璺宠繃**  
`broadcastRoomAndMaybeRefreshLobbyAfterHeartbeatTick`锛堜害鍗?*鏁寸涓嶅璇ユ埧鍋氫换浣?WS 骞挎挱**锛夈€?
鍥犳琚績璺宠涪鍑虹殑涓婃鐜╁锛屽湪婊¤冻銆屾湰绉掕烦杩囧箍鎾€嶇殑鎴挎€佺粍鍚堟椂锛?*鍙兘鍑虹幇鑷冲皯 1 绉掞紙鐩磋嚦鍚庣画鏌愮鍐嶅害骞挎挱涓烘锛変粛涓哄凡璁㈤槄 WS銆佸嵈宸蹭笉鍦?`players`/鎴垮唴鍚嶅崟鐨勭姸鎬?*銆傛帓鏌ョ敤渚嬫椂闇€鏄惧紡鏋勯€犺绐勬潯浠躲€?
鍙傝缂栨帓椤哄簭锛?```254:273:src/main/java/com/example/mgdemoplus/service/serviceImpl/DpRoomServiceImpl.java
        ...
        if (skipBroadcastForLoneSettledPlayerWaitingNothing(room)) {
            return;
        }
        ...
        broadcastRoomAndMaybeRefreshLobbyAfterHeartbeatTick(room, lobbyDirty);
```

---

## 3. 寤跺悗骞挎挱璺緞涓婄殑娆¤椋庨櫓锛圖edup锛?
`broadcastIfSubscribed` 鍦ㄣ€屽緟涓嬪彂 JSON銆嶄笌 **`lastBroadcastPayloadBySession` 涓婁竴娆″唴瀹圭浉鍚屻€嶆椂浼?**skip**銆傞€氬父浠庛€屾埧闂村唴蹇収 JSON銆嶅彉涓?`roomClosed` 浼氬彉鍖栵紱鑻ユ瀬绔儏鍐典笅涓婁竴杞凡鏄?`roomClosed` 鍗翠粛鐣欏湪闆嗗悎涓紙寮傚父璺緞锛夛紝鍙兘褰卞搷鏄惁鍐嶆涓嬪彂鈥斺€斿睘浣庢鐜囪竟鐣岋紝璁板綍鍦ㄦ鍗冲彲銆?
---

## 4. 瑙備紬 路 瓒呮椂绉诲嚭瑙備紬甯紙瀵圭収锛?
`tickEvictStaleSpectatorsOnHeartbeat` 鑻ヨ秴鏃跺垯 **`exitRoom(roomId, specNick)`**锛?*鍚屾牱涓嶄繚璇?*鍦ㄦ澶勭洿鎺?`shutdownSubscriptionsForNicknameInRoom`锛涘叧杩炲鍗婁粛渚濊禆鍚庣画鐨?**`broadcastIfSubscribed`** 璺緞锛堝悓涓?dedup / skip 绾︽潫閫傜敤锛夈€?
---

## 5. 缁欏洓涓?Agent 鐨勬帓鏌ユ竻鍗曪紙姣忛」椤荤瓟锛氭槸 / 鍚?/ 鏉′欢锛?
| 搴忓彿 | 闂 |
|------|------|
| Q1 | 涓婃鐪熶汉蹇冭烦瓒呮椂锛?*涓氬姟涓婂凡浠?`players` 绉婚櫎**锛屾湰绉掑唴 **`broadcastIfSubscribed` 鏄惁涓€瀹氭墽琛岋紵** |
| Q2 | 鑻ヤ笉鎵ц锛?*WS 浼氳瘽鏄惁浠嶅瓨鍦?`roomSessions`锛熸槸鍚︿粛鏀跺埌闈?`roomClosed` 蹇収锛?* |
| Q3 | **`shutdownSubscriptionsForNicknameInRoom`** 鍦ㄩ」鐩叾浠栬矾寰勬槸鍚﹀湪鐩稿悓璇箟涓嬭皟鐢紙鍙綔涓恒€岀珛鍗冲叧杩炪€嶅厹搴曞弬鑰冿級锛?|
| Q4 | 鍓嶇锛?*鏀跺埌 `roomClosed` 鍚庣殑瀵艰埅/鐘舵€佹竻鐞?*锛屼笌銆屼粎浠庡垪琛ㄦ秷澶便€嶄袱绉嶈矾寰勬槸鍚﹂兘瑕佹祴锛?|

---

## 6. 涓庢棦鏈?audit 鏂囨。鐨勬牎鍑?
鑻?`audit-agent-01` / `audit-agent-02` 涓啓鏈夈€屼笂妗屽績璺宠秴鏃?*宸?*璋冪敤 `shutdownSubscriptionsForNicknameInRoom`銆嶏紝璇蜂互**鏈妭 搂1 褰撳墠婧愮爜涓哄噯**鏇存柊琛ㄨ堪涓猴細**璋冪敤琚敞閲?鈫?涓昏闈犲箍鎾矾寰勫叧杩烇紙涓斿瓨鍦?搂2 绐楀彛锛?*銆?
---

*鏂囨。鐗堟湰渚濇嵁锛氫粨搴撳綋鍓?Java 婧愮爜锛涜矾鐢变笌鍓嶇缁嗚妭浠ュ疄鐜板绉颁负鍑嗐€?
