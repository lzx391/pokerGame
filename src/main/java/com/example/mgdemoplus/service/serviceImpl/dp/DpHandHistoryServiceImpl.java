package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.dto.DpHandHistoryDetailDTO;
import com.example.mgdemoplus.dto.DpHandHistoryListItemDTO;
import com.example.mgdemoplus.dto.DpHandHistoryPageDTO;
import com.example.mgdemoplus.entity.dp.DpObservedHandHistory;
import com.example.mgdemoplus.entity.dp.DpUser;
import com.example.mgdemoplus.mapper.dp.DpHandHistoryQueryMapper;
import com.example.mgdemoplus.mapper.dp.DpObservedHandHistoryMapper;
import com.example.mgdemoplus.mapper.dp.DpUserMapper;
import com.example.mgdemoplus.service.DpHandHistoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
//这个服务类是负责前端查询返回的
@Service
public class DpHandHistoryServiceImpl implements DpHandHistoryService{

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final DpHandHistoryQueryMapper queryMapper;
    private final DpUserMapper dpUserMapper;
    private final DpObservedHandHistoryMapper observedHandHistoryMapper;
    //这里是为了将payload_json字段转换为Map<String, Object>对象
    private final ObjectMapper objectMapper;

    public DpHandHistoryServiceImpl(
            DpHandHistoryQueryMapper queryMapper,
            DpUserMapper dpUserMapper,
            DpObservedHandHistoryMapper observedHandHistoryMapper,
            ObjectMapper objectMapper
    ) {
        this.queryMapper = queryMapper;
        this.dpUserMapper = dpUserMapper;
        this.observedHandHistoryMapper = observedHandHistoryMapper;
        this.objectMapper = objectMapper;
    }
    /**
     * 两人共同参与过的对局：同一手牌上两条参与者 JOIN；分页由 PageHelper + PageInfo（总条数 + 当前页）。
     * 列表行取「当前用户」一侧的座位与净筹码（与 /list 一致）。
     */
    public DpHandHistoryPageDTO checkUserAndOtherPlayerHandHistoryList(
            Integer userId,
            String otherNickname,
            String nickname,
            Integer otherUserId,
            int page,
            int pageSize
    ) {
        DpHandHistoryPageDTO out = new DpHandHistoryPageDTO();
        out.setPage(Math.max(page, 1));
        int size = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
        size = Math.min(size, MAX_PAGE_SIZE);
        out.setPageSize(size);

        if (nickname == null || nickname.isEmpty() || otherNickname == null || otherNickname.isEmpty()) {
            out.setTotal(0);
            out.setRecords(Collections.emptyList());
            return out;
        }

        if (userId != null) {
            DpUser u = dpUserMapper.selectById(userId);
            if (u == null || !nickname.equals(u.getNickname())) {
                out.setTotal(0);
                out.setRecords(Collections.emptyList());
                return out;
            }
            PageHelper.startPage(out.getPage(), size);
            List<DpHandHistoryListItemDTO> records = otherUserId != null
                    ? queryMapper.listCommonHandsBothUserIds(userId, nickname, otherUserId, otherNickname)
                    : queryMapper.listCommonHandsCurrentUserIdOtherNickname(userId, nickname, otherNickname);
            PageInfo<DpHandHistoryListItemDTO> pageInfo = new PageInfo<>(records);
            out.setTotal(pageInfo.getTotal());
            out.setRecords(records);
            return out;
        }

        PageHelper.startPage(out.getPage(), size);
        List<DpHandHistoryListItemDTO> records = queryMapper.listCommonHandsNicknameOnly(nickname, otherNickname);
        PageInfo<DpHandHistoryListItemDTO> pageInfo = new PageInfo<>(records);
        out.setTotal(pageInfo.getTotal());
        out.setRecords(records);
        return out;
    }
    /**
     * 当前登录用户（昵称必传；userId 若传须与 dp_user 中昵称一致）。
     *
     * @param page 从 1 开始
     */
    //已学习，本模块代码精讲如下：
    //1. listMyHandsPage方法：将userId和nickname传入，返回DpHandHistoryPageDTO对象
    //2. out = new DpHandHistoryPageDTO()：创建一个DpHandHistoryPageDTO对象
    //3. out.setPage(Math.max(page, 1))：设置页码
    //4. out.setPageSize(size)：设置每页条数
    //5. out.setTotal(total)：设置总条数
    //6. out.setRecords(records)：设置记录
    //7. return out：返回DpHandHistoryPageDTO对象
    public DpHandHistoryPageDTO listMyHandsPage(Integer userId, String nickname, int page, int pageSize) {
        DpHandHistoryPageDTO out = new DpHandHistoryPageDTO();
        out.setPage(Math.max(page, 1));//方便分页查询
        int size = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;//默认10条一页
        size = Math.min(size, MAX_PAGE_SIZE);//最多100条一页
        out.setPageSize(size);

        if (nickname == null || nickname.isEmpty()) {
            out.setTotal(0);
            out.setRecords(Collections.emptyList());
            return out;
        }

        if (userId != null) {
            DpUser u = dpUserMapper.selectById(userId);
            if (u == null || !nickname.equals(u.getNickname())) {
                out.setTotal(0);
                out.setRecords(Collections.emptyList());
                return out;
            }
            PageHelper.startPage(out.getPage(), size);
            List<DpHandHistoryListItemDTO> records = queryMapper.listForUserWithId(userId, nickname);
            PageInfo<DpHandHistoryListItemDTO> pageInfo = new PageInfo<>(records);
            out.setTotal(pageInfo.getTotal());
            out.setRecords(records);
            return out;
        }

        PageHelper.startPage(out.getPage(), size);//这里是计算偏移，代替limit
        List<DpHandHistoryListItemDTO> records = queryMapper.listForNicknameOnly(nickname);
        PageInfo<DpHandHistoryListItemDTO> pageInfo = new PageInfo<>(records);
        out.setTotal(pageInfo.getTotal());//算出总条数
        out.setRecords(records);
        return out;
    }

    /**
     * 单条牌谱详情：仅当当前用户在参与者表中关联该手时可读；
     * payload 含完整 holeCardsAtEnd（服务端归档），前端按街与弃牌时机决定展示。
     */
    //已学习，本模块代码精讲如下：
    //1. getDetail方法：将handHistoryId和userId和nickname传入，返回DpHandHistoryDetailDTO对象
    //2. if (nickname == null || nickname.isEmpty())：如果nickname为空，则返回null
    //3. if (userId != null)：如果userId不为空，则说明当前用户在参与者表中，所以需要查询参与者表
    //4. if (u == null || !nickname.equals(u.getNickname()))：如果用户不存在或者昵称不匹配，则返回null
    //5. if (queryMapper.countParticipantForHandWithUserId(handHistoryId, userId, nickname) == 0)：如果当前用户不在参与者表中，则返回null
    //6. if (queryMapper.countParticipantForHandNicknameOnly(handHistoryId, nickname) == 0)：如果当前用户不在参与者表中，则返回null
    //7. return null：返回null
    //8. row = observedHandHistoryMapper.selectById(handHistoryId)：查询牌谱主表
    //9. if (row == null)：如果牌谱不存在，则返回null
    //10. payload = objectMapper.readValue(row.getPayloadJson(), new TypeReference<Map<String, Object>>() {})：将payload_json字段转换为Map<String, Object>对象
    //11. out = new DpHandHistoryDetailDTO()：创建一个DpHandHistoryDetailDTO对象
    //12. out.setHandHistoryId(row.getId())：设置手牌谱id
    //13. out.setRoomId(row.getRoomId())：设置房间id
    //14. out.setHandSeed(row.getHandSeed())：设置手牌谱种子
    //15. out.setStartedAtMs(row.getStartedAtMs())：设置开始时间
    //16. out.setEndedAtMs(row.getEndedAtMs())：设置结束时间
    //17. out.setSmallBlindChips(row.getSmallBlindChips())：设置小盲注
    //18. out.setBigBlindChips(row.getBigBlindChips())：设置大盲注
    //19. out.setDealerNickname(row.getDealerNickname())：设置庄家昵称
    //20. out.setMainPotBeforeSettlement(row.getMainPotBeforeSettlement())：设置主池
    //21. out.setPayloadVersion(row.getPayloadVersion())：设置payload版本
    //22. out.setPayload(payload)：设置payload
    //23. return out：返回DpHandHistoryDetailDTO对象
    public DpHandHistoryDetailDTO getDetail(long handHistoryId, Integer userId, String nickname) {

        if (nickname == null || nickname.isEmpty()) {
            return null;
        }

        //如果userId不为空，则说明当前用户在参与者表中，所以需要查询参与者表
        if (userId != null) {
            DpUser u = dpUserMapper.selectById(userId);
            if (u == null || !nickname.equals(u.getNickname())) {
                return null;
            }
            //如果返回0说明当前用户不在参与者表中，所以返回null
            if (queryMapper.countParticipantForHandWithUserId(handHistoryId, userId, nickname) == 0) {
                return null;
            }
        } else {
            //如果userId为空，则说明当前用户不在参与者表中，所以需要查询参与者表
            if (queryMapper.countParticipantForHandNicknameOnly(handHistoryId, nickname) == 0) {
                return null;
            }
        }
        //以上是防御性编程，正片开始
        DpObservedHandHistory row = observedHandHistoryMapper.selectById(handHistoryId);
        if (row == null) {
            return null;
        }
//以下是解析payload_json字段，转化成Map<String, Object>对象
        Map<String, Object> payload;
        try {
            //这里是为了将payload_json字段转换为Map<String, Object>对象
            payload = objectMapper.readValue(
                    row.getPayloadJson(),//这里是拿里面的payload_json字段
                    new TypeReference<Map<String, Object>>() {//这里是为了指定转换后的类型
                    }
            );
        } catch (Exception e) {
            return null;
        }
//以下是将row中的数据赋值给DpHandHistoryDetailDTO对象
        DpHandHistoryDetailDTO out = new DpHandHistoryDetailDTO();
        out.setHandHistoryId(row.getId());
        out.setRoomId(row.getRoomId());
        out.setHandSeed(row.getHandSeed());
        out.setStartedAtMs(row.getStartedAtMs());
        out.setEndedAtMs(row.getEndedAtMs());
        out.setSmallBlindChips(row.getSmallBlindChips());
        out.setBigBlindChips(row.getBigBlindChips());
        out.setDealerNickname(row.getDealerNickname());
        out.setMainPotBeforeSettlement(row.getMainPotBeforeSettlement());
        out.setPayloadVersion(row.getPayloadVersion());
        out.setPayload(payload);
        return out;
    }
   
}
