# BOT_LLM涉及类有  
[DpNpcEngine.java](../../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/DpNpcEngine.java)  
[LlmNpc.java](../../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/npc/LlmNpc.java)  
[LlmNpcGameContext.java](../../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/npc/LlmNpcGameContext.java)  
[DpLlmNpcContextMapper.java](../../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/DpLlmNpcContextMapper.java)  
[DpLlmNpcDecisionService.java](../../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/DpLlmNpcDecisionService.java)  
调用关系如下，由room服务类的定时器进入DpLlmNpcDecisionService，相关学习注释已加入该类，构造器用到的大模型id,大模型key,baseUrl由LlmNpc负责，发给AI的情报包由DpLlmNpcContextMapper调用LlmNpcGameContext，npc引擎里情报转化，BOT_LLM枚举来实现  

