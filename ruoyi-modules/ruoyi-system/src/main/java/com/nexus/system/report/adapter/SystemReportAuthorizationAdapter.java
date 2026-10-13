package com.nexus.system.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Live account/role/package resolver. No JWT or request context is propagated to workers. */
@Service
@DS("master")
public class SystemReportAuthorizationAdapter implements ReportAuthorizationPort {
    private final JdbcTemplate jdbc;
    public SystemReportAuthorizationAdapter(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private record User(long id,String tenant,long department,String status,String deleted) { }
    private record Tenant(String id,Long packageId,Timestamp expires) { }
    private record Role(long id,String scope) { }
    private record Department(long id,String ancestors) { }
    public ReportActor resolve(String requestedTenant,long userId){
        if(requestedTenant==null||!requestedTenant.matches("[A-Za-z0-9_-]{1,20}")||userId<=0)throw denied();
        var users=jdbc.query("SELECT user_id,tenant_id,dept_id,status,del_flag FROM sys_user WHERE user_id=?",(rs,index)->new User(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getString(4),rs.getString(5)),userId);
        if(users.size()!=1||!"0".equals(users.get(0).status)||!"0".equals(users.get(0).deleted))throw denied();
        var tenants=jdbc.query("SELECT tenant_id,package_id,expire_time FROM sys_tenant WHERE tenant_id=? AND status='0' AND del_flag='0'",(rs,index)->new Tenant(rs.getString(1),(Long)rs.getObject(2),rs.getTimestamp(3)),requestedTenant);
        if(tenants.size()!=1||tenants.get(0).expires!=null&&!tenants.get(0).expires.toInstant().isAfter(Instant.now()))throw denied();
        User user=users.get(0);Tenant tenant=tenants.get(0);
        if(user.id==1)return new ReportActor(tenant.id,user.id,user.department,true,List.of());
        if(!tenant.id.equals(user.tenant))throw denied();
        Set<Long> packageMenus=null;
        if(tenant.packageId!=null&&tenant.packageId>0){
            var packages=jdbc.queryForList("SELECT menu_ids FROM sys_tenant_package WHERE package_id=? AND status='0' AND del_flag='0'",String.class,tenant.packageId);
            if(packages.size()!=1)throw denied();packageMenus=new HashSet<>();
            String csv=packages.get(0);if(csv!=null)for(String value:csv.split(",")){
                if(value.trim().isEmpty())continue;try{packageMenus.add(Long.parseLong(value.trim()));}catch(NumberFormatException e){throw denied();}
            }
        }
        var roles=jdbc.query("SELECT r.role_id,r.data_scope FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.role_id WHERE ur.user_id=? AND r.tenant_id=? AND r.status='0' AND r.del_flag='0' ORDER BY r.role_id",(rs,index)->new Role(rs.getLong(1),rs.getString(2)),user.id,tenant.id);
        var departments=jdbc.query("SELECT dept_id,ancestors FROM sys_dept WHERE tenant_id=? AND status='0' AND del_flag='0'",(rs,index)->new Department(rs.getLong(1),rs.getString(2)),tenant.id);
        Set<Long> activeDepartments=new HashSet<>();departments.forEach(d->activeDepartments.add(d.id));
        List<ReportRole> resolved=new ArrayList<>();
        for(Role role:roles){
            Set<String> permissions=new TreeSet<>();
            for(var menu:jdbc.queryForList("SELECT m.menu_id,m.perms FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE rm.role_id=? AND m.status='0' AND m.perms IS NOT NULL",role.id)){
                long id=((Number)menu.get("menu_id")).longValue();String permission=Objects.toString(menu.get("perms"),"");
                if((packageMenus==null||packageMenus.contains(id))&&!permission.isBlank())permissions.add(permission);
            }
            ReportScope scope=switch(Objects.toString(role.scope,"")){
                case "1"->ReportScope.all(user.id);
                case "2"->{Set<Long> ids=new TreeSet<>(jdbc.queryForList("SELECT d.dept_id FROM sys_role_dept rd JOIN sys_dept d ON d.dept_id=rd.dept_id WHERE rd.role_id=? AND d.tenant_id=? AND d.status='0' AND d.del_flag='0'",Long.class,role.id,tenant.id));yield new ReportScope(false,ids,false,user.id);}
                case "3"->new ReportScope(false,activeDepartments.contains(user.department)?Set.of(user.department):Set.of(),false,user.id);
                case "4","6"->{Set<Long> ids=new TreeSet<>();if(activeDepartments.contains(user.department))for(Department department:departments){if(department.id==user.department||ancestor(department.ancestors,user.department))ids.add(department.id);}yield new ReportScope(false,ids,"6".equals(role.scope),user.id);}
                case "5"->new ReportScope(false,Set.of(),true,user.id);
                default->throw denied();
            };
            if(scope.departments().size()>2000)throw new DatasetAccessException("CAPACITY_EXCEEDED","部门权限范围超过首期查询上限");
            resolved.add(new ReportRole(role.id,permissions,scope));
        }
        return new ReportActor(tenant.id,user.id,user.department,false,resolved);
    }
    private static boolean ancestor(String ancestors,long department){if(ancestors==null)return false;return Arrays.stream(ancestors.split(",")).anyMatch(value->value.trim().equals(Long.toString(department)));}
    public boolean subjectExists(String tenant,String type,long id){
        if(id<=0)return false;String table,column;
        switch(type){case "USER"->{table="sys_user";column="user_id";}case "ROLE"->{table="sys_role";column="role_id";}case "DEPT"->{table="sys_dept";column="dept_id";}default->{return false;}}
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+column+"=? AND tenant_id=? AND status='0' AND del_flag='0'",Long.class,id,tenant);return count!=null&&count==1;
    }
    private static DatasetAccessException denied(){return new DatasetAccessException("SCOPE_DENIED","报表执行主体、租户或授权配置无效");}
}
