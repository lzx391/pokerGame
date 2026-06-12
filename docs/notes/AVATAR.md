# 关于头像的加载处理  
一共做了三层处理，分别是nginx指明可缓存，提前读预热，小图传粗略处理版本  
## nginx指明可缓存
这里指的是在nginx的配置上写清楚浏览器是可以缓存这个路径下的资源的，不用每次都来请求后端
    location ^~ /images/ {
        proxy_pass http://app:8088;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_hide_header Cache-Control;
        proxy_hide_header Expires;
        proxy_hide_header Pragma;
        add_header Cache-Control "public, max-age=31536000" always;
    }
## 提前读预热
这里指的是当玩家进入大厅的时候，会发送一次friend请求，然后获取好友列表json数据，然后提前先去后端取出来资源到本地缓存，等正式点开好友列表就不用再一个一个请求了
## 粗略图
这里指的是传头像的时候，存一份完整版存一份精简版，当玩家查看个人资料的时候，因为头像很大要有细节，所以放完整原图，当在大厅或者是预览好友列表，好友对话时，头像很小，所以加载粗略图即可，比较省流量