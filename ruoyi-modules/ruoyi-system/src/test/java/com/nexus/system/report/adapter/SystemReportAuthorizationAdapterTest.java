package com.nexus.system.report.adapter;
import com.nexus.common.report.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SystemReportAuthorizationAdapterTest {
    private JdbcTemplate database(){
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE sys_user(user_id BIGINT,tenant_id VARCHAR(20),dept_id BIGINT,status CHAR,del_flag CHAR)");
        jdbc.execute("CREATE TABLE sys_tenant(tenant_id VARCHAR(20),package_id BIGINT,expire_time TIMESTAMP,status CHAR,del_flag CHAR)");
        jdbc.execute("CREATE TABLE sys_tenant_package(package_id BIGINT,menu_ids VARCHAR(3000),status CHAR,del_flag CHAR)");
        jdbc.execute("CREATE TABLE sys_role(role_id BIGINT,tenant_id VARCHAR(20),data_scope CHAR,status CHAR,del_flag CHAR)");
        jdbc.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT)");jdbc.execute("CREATE TABLE sys_role_menu(role_id BIGINT,menu_id BIGINT)");
        jdbc.execute("CREATE TABLE sys_role_dept(role_id BIGINT,dept_id BIGINT)");
        jdbc.execute("CREATE TABLE sys_menu(menu_id BIGINT,perms VARCHAR(100),status CHAR)");
        jdbc.execute("CREATE TABLE sys_dept(dept_id BIGINT,tenant_id VARCHAR(20),ancestors VARCHAR(200),status CHAR,del_flag CHAR)");
        jdbc.update("INSERT INTO sys_tenant VALUES('A',1,NULL,'0','0'),('B',2,NULL,'0','0')");
        jdbc.update("INSERT INTO sys_tenant_package VALUES(1,'10,11,12','0','0'),(2,'10','0','0')");
        jdbc.update("INSERT INTO sys_user VALUES(7,'A',100,'0','0'),(8,'B',200,'0','0'),(1,'A',100,'0','0')");
        jdbc.update("INSERT INTO sys_role VALUES(1,'A','1','0','0'),(2,'A','4','0','0'),(3,'B','1','0','0')");
        jdbc.update("INSERT INTO sys_user_role VALUES(7,1),(7,2),(7,3)");
        jdbc.update("INSERT INTO sys_role_menu VALUES(1,10),(1,13),(2,11),(2,12),(3,11)");
        jdbc.update("INSERT INTO sys_menu VALUES(10,'report:definition:list','0'),(11,'wm:stock:list','0'),(12,'wm:stock:query','0'),(13,'wm:stock:remove','0')");
        jdbc.update("INSERT INTO sys_dept VALUES(100,'A','0','0','0'),(101,'A','0,100','0','0'),(200,'B','0,100','0','0')");return jdbc;
    }
    @Test void roleScopesAndTenantPackageLimitsAreReReadFromTheDatabase(){
        var jdbc=database();var resolver=new SystemReportAuthorizationAdapter(jdbc);var actor=resolver.resolve("A",7);
        assertTrue(actor.has("wm:stock:list"));assertFalse(actor.has("wm:stock:remove"));
        assertEquals(Set.of(100L,101L),actor.scopeFor("wm:stock:list").departments());assertFalse(actor.scopeFor("wm:stock:list").all());
        assertFalse(actor.roleIds().contains(3L));
        jdbc.update("UPDATE sys_role_menu SET menu_id=10 WHERE role_id=2");assertFalse(resolver.resolve("A",7).has("wm:stock:list"));
    }
    @Test void crossTenantDisabledAndExpiredActorsAreRejected(){
        var jdbc=database();var resolver=new SystemReportAuthorizationAdapter(jdbc);assertThrows(DatasetAccessException.class,()->resolver.resolve("B",7));
        jdbc.update("UPDATE sys_user SET status='1' WHERE user_id=7");assertThrows(DatasetAccessException.class,()->resolver.resolve("A",7));
        jdbc.update("UPDATE sys_user SET status='0' WHERE user_id=7");jdbc.update("UPDATE sys_tenant SET expire_time=TIMESTAMP '2020-01-01 00:00:00' WHERE tenant_id='A'");assertThrows(DatasetAccessException.class,()->resolver.resolve("A",7));
    }
    @Test void platformAdminStillRequiresAnActiveExplicitTenantAndGrantSubjectsBelongToIt(){
        var resolver=new SystemReportAuthorizationAdapter(database());assertTrue(resolver.resolve("B",1).superAdmin());
        assertTrue(resolver.subjectExists("A","USER",7));assertFalse(resolver.subjectExists("A","USER",8));
        assertFalse(resolver.subjectExists("A","ROLE",3));assertFalse(resolver.subjectExists("A","USER OR 1=1",7));
    }
}
