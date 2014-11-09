### 公告
近期有很多热衷开源的geek们在问最新代码的更新日期，我们在此说明一下，由于近期工程师们都在备战双十一，开源的投入会相对减少，所以我们把提交最新代码的时间定在2014年11月18日，非常感谢大家对TeamTalk的关注和支持~具体安排如下：
* 11.11之前工程师全力备战双十一，请见谅
* 11.12～11.14 C++ Server, Java DB Proxy, PHP, android, iOS, Win Client代码移植, MAC Client延后（功能还未完全）
* 11.15～11.17 测试TeamTalk，包括PHP，android, iOS, Win Client 端功能走通，测试一键部署脚本
* 11.18  上传代码并正式发布

###简介：

TeamTalk是一套开源的企业办公即时通讯软件，作为整套系统的组成部分之一，TTServer为TeamTalk 客户端提供用户登录，消息转发及存储等基础服务。

TTServer主要包含了以下几种服务器:

- LoginServer (C++): 登录服务器，分配一个负载小的MsgServer给客户端使用
- MsgServer (C++):  消息服务器，提供客户端大部分信令处理功能，包括私人聊天、群组聊天等
- RouteServer (C++):  路由服务器，为登录在不同MsgServer的用户提供消息转发功能
- FileServer (C++): 文件服务器，提供客户端之间得文件传输服务，支持在线以及离线文件传输
- MsfsServer (C++): 图片存储服务器，提供头像，图片传输中的图片存储服务
- DBProxy (JAVA): 数据库代理服务器，提供mysql以及redis的访问服务，屏蔽其他服务器与mysql与redis的直接交互


###当前支持的功能点：

- 私人聊天
- 群组聊天
- 文件传输
- 多点登录
- 组织架构设置.


###系统结构图

![](https://github.com/mogutt/TTServer/blob/master/docs/pics/server.png)


###后续可考虑的功能

- 协议加密
- 手机推送
- 其他合理的酷炫功能点


###C++编译
- 整体编译:可以运行src/目录下的build.sh脚本,例如: ./build.sh version 0.0.1
- 单个模块编译:进入各自的目录,然后执行make即可,注意:base模块需要优先编译

###C++使用
- 程序启动请使用run.sh脚本,例如: ./run.sh start
- 程序重启请使用restart.sh脚本,例如: ./restart.sh msg_server

###C++部署方案
- 部署方案详见https://github.com/mogutt/TTAutoDeploy 之IM_SERVER模块


###java编译
— 编译整个项目可以运行与src同目录的packageproduct.sh, sh packageproduct.sh

###java使用
— 程序启动可以运行与src同目录的startup.sh, sh startup.sh 10400(其中10400为绑定的端口号)

###java部署方案
- 部署方案详见https://github.com/mogutt/TTAutoDeploy 之IM_SERVER模块
