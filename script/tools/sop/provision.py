"""User-authorized SOP config creation via ordinary authenticated application API.

Secrets remain in memory/private session input; no database/default/cache bypass.
"""
from dataclasses import dataclass,field
import json
from pathlib import Path
import requests
from storage import SafetyError,S3Settings,require

PUBLIC_FIELDS = ('ossConfigId','configKey','bucketName','endpoint','accessPolicy','status','isHttps','domain','prefix','region')

@dataclass(frozen=True)
class AppSession:
    token: str = field(repr=False)
    client_id: str = field(repr=False)
    tenant_id: str = '000000'

    def __post_init__(self):
        require(bool(self.token) and bool(self.client_id) and self.tenant_id=='000000','APP_CREDENTIALS_REQUIRED')

    @classmethod
    def from_private_file(cls,path):
        path=Path(path)
        require(path.is_file() and not path.is_symlink() and path.stat().st_size<16384,'PRIVATE_SESSION_REJECTED')
        data=json.loads(path.read_text(encoding='utf-8-sig'))
        require(data.get('baseUrl')=='http://127.0.0.1:8080','APP_ORIGIN_REJECTED')
        return cls(data.get('token',''),data.get('clientId',''),data.get('tenantId',''))

    def request(self,method,path,payload=None):
        allowed_get=path in ('/system/user/getInfo','/resource/oss/config/list')
        require(method=='GET' and allowed_get or method=='POST' and path=='/resource/oss/config','APP_ROUTE_REJECTED')
        if method=='POST':
            require(isinstance(payload,dict) and all(payload.get(k)==v for k,v in {'configKey':'sop','bucketName':'sop','endpoint':'159.75.14.226:9005','accessPolicy':'0','status':'1','isHttps':'N','prefix':'','domain':''}.items()),'APP_CONFIG_PAYLOAD_REJECTED')
        session=requests.Session();session.trust_env=False
        try:
            response=session.request(method,'http://127.0.0.1:8080'+path,
                headers={'Authorization':'Bearer '+self.token,'clientid':self.client_id},
                params={'pageNum':1,'pageSize':100} if path.endswith('/list') else None,
                json=payload,timeout=20,allow_redirects=False)
            require(response.status_code==200,'APP_HTTP_REJECTED')
            document=response.json()
            require(isinstance(document,dict) and document.get('code')==200,'APP_BUSINESS_REQUEST_REJECTED')
            return document
        except SafetyError:raise
        except Exception:raise SafetyError('APP_REQUEST_UNAVAILABLE') from None
        finally:session.close()

    def verify_identity(self):
        document=self.request('GET','/system/user/getInfo')
        data=document.get('data',{});user=data.get('user',{})
        require(user.get('tenantId')==self.tenant_id and user.get('userId') is not None,'APP_TENANT_UNVERIFIED')
        permissions=set(data.get('permissions',[]))
        require('*:*:*' in permissions or {'system:ossConfig:list','system:ossConfig:add'}<=permissions,'APP_CONFIG_PERMISSION_MISSING')
        return {'authenticated':True,'tenantVerified':True,'configPermissionVerified':True}


def config_payload(settings):
    require(settings.endpoint=='159.75.14.226:9005' and settings.bucket=='sop','SOP_CONFIG_TARGET_REQUIRED')
    return {'configKey':'sop','accessKey':settings.access_key,'secretKey':settings.secret_key,
            'bucketName':'sop','prefix':'','endpoint':settings.endpoint,'domain':'','isHttps':'N',
            'region':'us-east-1','accessPolicy':'0','status':'1',
            'remark':'SOP专用私有桶，参考稿及受控版本附件；非默认，不修改现有ruoyi桶权限。'}


def _public(row):return {k:row.get(k) for k in PUBLIC_FIELDS}

def validate_config_rows(before,after):
    old={str(r['ossConfigId']):r for r in before if r.get('configKey')!='sop'}
    current={str(r['ossConfigId']):r for r in after if r.get('configKey')!='sop'}
    require(set(old)==set(current) and all(old[k]==current[k] for k in old),'EXISTING_OSS_CONFIG_CHANGED')
    sop=[r for r in after if r.get('configKey')=='sop']
    require(len(sop)==1,'UNIQUE_SOP_CONFIG_REQUIRED')
    require(all(sop[0].get(k)==v for k,v in {'configKey':'sop','bucketName':'sop','endpoint':'159.75.14.226:9005','accessPolicy':'0','status':'1','isHttps':'N','prefix':'','domain':''}.items()),'SOP_CONFIG_PRIVATE_NONDEFAULT_REQUIRED')
    require(any(r.get('configKey')=='minio' and r.get('status')=='0' for r in after),'DEFAULT_MINIO_CONFIG_CHANGED')
    return sop[0]


def create_nondefault_config(session,settings):
    before_doc=session.request('GET','/resource/oss/config/list')
    before=before_doc.get('rows',[])
    require(before_doc.get('total')==len(before),'FULL_CONFIG_LIST_REQUIRED')
    exists=[r for r in before if r.get('configKey')=='sop']
    if not exists:
        session.request('POST','/resource/oss/config',config_payload(settings))
    after_doc=session.request('GET','/resource/oss/config/list')
    after=after_doc.get('rows',[])
    require(after_doc.get('total')==len(after),'FULL_CONFIG_LIST_REQUIRED')
    row=validate_config_rows(before,after)
    require(row.get('accessKey')==settings.access_key and row.get('secretKey')==settings.secret_key,'SOP_CONFIG_CREDENTIALS_MISMATCH')
    return {'configCreated':not bool(exists),'ossConfigId':str(row['ossConfigId']),'configKey':'sop','bucketName':'sop','endpoint':settings.endpoint,'accessPolicy':'PRIVATE','isDefault':False,'existingConfigsUnchanged':True}
