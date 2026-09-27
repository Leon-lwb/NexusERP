$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..\..\..')).Path
$values=@{}
foreach($line in Get-Content -LiteralPath "$root\doc\ai\benchmark\delivery-risk\control\.env"){
 if($line -notmatch '^(DECISION_DEMO_[A-Z_]+)=([A-Za-z0-9_./-]+)$'){throw 'Invalid CONTROL env'}
 $values[$Matches[1]]=$Matches[2]
 [Environment]::SetEnvironmentVariable($Matches[1],$Matches[2],'Process')
}
$marker=Join-Path $env:TEMP 'nexus-control-faults'
New-Item -ItemType Directory -Force -Path $marker | Out-Null
if(Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue){throw '8081 occupied'}
$argsList=@('-Dfile.encoding=UTF-8',"-Dloader.path=$root\ruoyi-admin\target\test-classes",'-cp',"$root\ruoyi-admin\target\ruoyi-admin.jar",'org.springframework.boot.loader.launch.PropertiesLauncher','--spring.profiles.active=dev,decision-control-test','--server.port=8081','--captcha.enable=false','--spring.datasource.dynamic.p6spy=false','--decision.control.enabled=true',"--decision.control.marker-directory=$marker",'--nexus.ai.decision.max-concurrent-runs=48','--spring.data.redis.host=127.0.0.1','--spring.data.redis.port=6386',"--spring.data.redis.password=$($values.DECISION_DEMO_REDIS_PASSWORD)",'--spring.rabbitmq.host=127.0.0.1','--spring.rabbitmq.port=5685',"--spring.rabbitmq.username=$($values.DECISION_DEMO_MQ_USER)","--spring.rabbitmq.password=$($values.DECISION_DEMO_MQ_PASSWORD)",'--spring.rabbitmq.virtual-host=decision-control-20260927','--logging.level.org.dromara=INFO','--logging.level.com.nexus=INFO')
foreach($name in @('master','erp','mes','iot')){
 $suffix=if($name -eq 'master'){'sys'}else{$name}
 $url="jdbc:mysql://127.0.0.1:13307/nexus_ai_decision_control_20260927_$($suffix)?useUnicode=true&characterEncoding=utf8&connectionCollation=utf8mb4_unicode_ci&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8&autoReconnect=true&rewriteBatchedStatements=true&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true"
 $argsList += "--spring.datasource.dynamic.datasource.$name.url=$url"
 $argsList += "--spring.datasource.dynamic.datasource.$name.username=$($values.DECISION_DEMO_DB_USER)"
 $argsList += "--spring.datasource.dynamic.datasource.$name.password=$($values.DECISION_DEMO_DB_PASSWORD)"
}
Write-Output 'CONTROL 8081 with external test-classes, four isolated schemas, Redis6386/private MQ; secrets hidden'
& java @argsList *> "$env:TEMP\nexus-control-backend.log"
exit $LASTEXITCODE
