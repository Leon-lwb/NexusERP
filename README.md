<p align="center">
	<img alt="logo" src="https://oscimg.oschina.net/oscnet/up-d3d0a9303e11d522a06cd263f3079027715.png">
</p>
<h1 align="center" style="margin: 30px 0 30px; font-weight: bold;">NexusERP</h1>
<h4 align="center">基于若依SpringBoot+Vue前后端分离的Java快速开发框架</h4>
<p align="center">
	<a href="https://gitee.com/helloworld_wh18/nexus-erp"><img src="https://gitee.com/helloworld_wh18/nexus-erp/badge/star.svg?theme=dark"></a>
	<a href="https://gitee.com/helloworld_wh18/nexus-erp"><img src="https://img.shields.io/badge/RuoYi-v3.9.0-brightgreen.svg"></a>
	<a href="https://gitee.com/helloworld_wh18/nexus-erp/blob/master/LICENSE"><img src="https://img.shields.io/github/license/mashape/apistatus.svg"></a>
</p>

## 平台简介

NEXUS-ERP是一套全部开源的ERP企业管理平台，毫无保留给个人及企业免费使用。

* 前端采用Vue、Element UI。
* 后端采用Spring Boot、Spring Security、Redis & Jwt。
* 权限认证使用Jwt，支持多终端认证系统。
* 支持加载动态权限菜单，多方式轻松权限控制。
* 高效率开发，使用代码生成器可以一键生成前后端代码。
* 集成了flowable流程业务。

## 内置功能

1. 用户管理：用户是系统操作者，该功能主要完成系统用户配置。
2. 部门管理：配置系统组织机构（公司、部门、小组），树结构展现支持数据权限。
3. 岗位管理：配置系统用户所属担任职务。
4. 菜单管理：配置系统菜单，操作权限，按钮权限标识等。
5. 角色管理：角色菜单权限分配、设置角色按机构进行数据范围权限划分。
6. 字典管理：对系统中经常使用的一些较为固定的数据进行维护。
7. 参数管理：对系统动态配置常用参数。
8. 通知公告：系统通知公告信息发布维护。
9. 操作日志：系统正常操作日志记录和查询；系统异常信息日志记录和查询。
10. 登录日志：系统登录日志记录查询包含登录异常。
11. 在线用户：当前系统中活跃用户状态监控。
12. 定时任务：在线（添加、修改、删除)任务调度包含执行结果日志。
13. 表设计器：在线设计生成数据库表结构。
14. 代码生成：前后端代码的生成（java、html、xml、sql）支持CRUD下载 。
15. 表单设计：在线设计生成vue3表单代码。
16. 系统接口：根据业务代码自动生成相关的api接口文档。
17. 服务监控：监视当前系统CPU、内存、磁盘、堆栈等相关信息。
18. 缓存监控：对系统的缓存信息查询，命令统计等。
19. 在线构建器：拖动表单元素生成相应的HTML代码。
20. 连接池监视：监视当前系统数据库连接池状态，可进行分析SQL找出系统性能瓶颈。
21. 主数据：系统基础数据维护。
22. 销售管理：销售订单、销售合同、销售订单等
23. 采购管理：采购订单、采购合同、采购订单等
24. 库存管理：库存盘点、库存调拨等
25. 生产管理：生产订单、生产合同、生产订单等
26. 生产排班：节假日设置、工人档案、班组设置、排班计划等
27. 考勤管理：打卡规则、考勤记录等
28. 流程管理：流程分类、流程设计、流程模型、部署管理、业务表单等
29. 任务管理：新建流程、我的流程、代办任务、代签任务、已办任务、抄送我的等

## 在线体验

- admin/admin123  

演示地址：http://nexuserp.website/

软件部署：
  后端部署：运行 package.bat 文件打jar包，服务器启动jar包（nohup java -jar nexus-admin.jar > nexus.log 2>&1 & > nexus.log 2>&1）。
  前端部署：终端运行 npm run build:prod

文档地址：
## 演示图

<table>
    <tr>
        <td><img src="https://oscimg.oschina.net/oscnet/cd1f90be5f2684f4560c9519c0f2a232ee8.jpg"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/1cbcf0e6f257c7d3a063c0e3f2ff989e4b3.jpg"/></td>
    </tr>
    <tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-8074972883b5ba0622e13246738ebba237a.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-9f88719cdfca9af2e58b352a20e23d43b12.png"/></td>
    </tr>
    <tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-39bf2584ec3a529b0d5a3b70d15c9b37646.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-936ec82d1f4872e1bc980927654b6007307.png"/></td>
    </tr>
	<tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-b2d62ceb95d2dd9b3fbe157bb70d26001e9.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-d67451d308b7a79ad6819723396f7c3d77a.png"/></td>
    </tr>	 
    <tr>
        <td><img src="https://oscimg.oschina.net/oscnet/5e8c387724954459291aafd5eb52b456f53.jpg"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/644e78da53c2e92a95dfda4f76e6d117c4b.jpg"/></td>
    </tr>
	<tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-8370a0d02977eebf6dbf854c8450293c937.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-49003ed83f60f633e7153609a53a2b644f7.png"/></td>
    </tr>
	<tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-d4fe726319ece268d4746602c39cffc0621.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-c195234bbcd30be6927f037a6755e6ab69c.png"/></td>
    </tr>
    <tr>
        <td><img src="https://oscimg.oschina.net/oscnet/b6115bc8c31de52951982e509930b20684a.jpg"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-5e4daac0bb59612c5038448acbcef235e3a.png"/></td>
    </tr>
	<tr>
        <td>![](https://gitee.com/helloworld_wh18/nexus-erp/raw/master/doc/image/%E4%B8%BB%E6%95%B0%E6%8D%AE-%E8%AE%A1%E9%87%8F%E5%8D%95%E4%BD%8D.png)</td>
        <td>![](https://gitee.com/helloworld_wh18/nexus-erp/raw/master/doc/image/%E9%94%80%E5%94%AE%E7%AE%A1%E7%90%86-%E5%AE%A2%E6%88%B7%E8%AE%A2%E5%8D%95.png)</td>
    </tr>
	<tr>
        <td>![](https://gitee.com/helloworld_wh18/nexus-erp/blob/master/doc/image/%E5%BA%93%E6%88%BF%E7%AE%A1%E7%90%86-%E9%87%87%E8%B4%AD%E5%85%A5%E5%BA%93.png)</td>
        <td>![](https://gitee.com/helloworld_wh18/nexus-erp/blob/master/doc/image/%E7%94%9F%E4%BA%A7%E7%AE%A1%E7%90%86-%E7%94%9F%E4%BA%A7%E8%AE%A1%E5%88%92.png)</td>
    </tr>
    <tr>
        <td>![](https://gitee.com/helloworld_wh18/nexus-erp/blob/master/doc/image/%E8%80%83%E5%8B%A4%E7%AE%A1%E7%90%86-%E8%80%83%E5%8B%A4%E8%AE%B0%E5%BD%95.png)</td>
        <td>![](https://gitee.com/helloworld_wh18/nexus-erp/blob/master/doc/image/%E7%94%9F%E4%BA%A7%E6%8E%92%E7%8F%AD-%E8%8A%82%E5%81%87%E6%97%A5%E8%AE%BE%E7%BD%AE.png)</td>
    </tr>
    <tr>
        <td>![](https://gitee.com/helloworld_wh18/nexus-erp/blob/master/doc/image/%E6%B5%81%E7%A8%8B%E7%AE%A1%E7%90%86-%E6%B5%81%E7%A8%8B%E6%A8%A1%E5%9E%8B.png)</td>
        <td>![](https://gitee.com/helloworld_wh18/nexus-erp/blob/master/doc/image/%E4%BB%BB%E5%8A%A1%E7%AE%A1%E7%90%86-%E5%B7%B2%E5%8A%9E%E4%BB%BB%E5%8A%A1.png)</td>
    </tr>

</table>