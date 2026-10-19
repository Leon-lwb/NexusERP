package com.nexus.common.trace;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
/** A cursor is bound to its tenant, subject, and section. It carries no authority. */
public final class TraceCursor {
    private TraceCursor(){}
    public static String encode(String tenant,Long id,String section,long after){return Base64.getUrlEncoder().withoutPadding().encodeToString((tenant+":"+id+":"+section+":"+after).getBytes(StandardCharsets.UTF_8));}
    public static long decode(String cursor,String tenant,Long id,String section){
        if(cursor==null || cursor.isBlank())return 0;
        try {
            String[] fields=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8).split(":",-1);
            if(fields.length!=4 || !fields[0].equals(tenant) || !fields[1].equals(id.toString()) || !fields[2].equals(section))throw new IllegalArgumentException();
            long after=Long.parseLong(fields[3]);if(after<=0)throw new IllegalArgumentException();return after;
        }catch(Exception e){throw new IllegalArgumentException("追溯游标不属于当前身份或范围");}
    }
}
