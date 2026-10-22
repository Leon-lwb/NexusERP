-- AI 第二阶段：仅适用于系统master；本增量不自动执行、不授予角色权限。
-- MySQL5.7兼容。新增表可重复执行；不导入主安装SQL到存量库。
CREATE TABLE IF NOT EXISTS ai_governance_lock (
 tenant_id varchar(20) NOT NULL, PRIMARY KEY (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_model_call (
 call_id varchar(64) NOT NULL, tenant_id varchar(20) NOT NULL, user_id bigint NOT NULL, user_name varchar(128),
 request_id varchar(64), session_id bigint, flow_instance_id bigint, decision_run_id bigint, ab_run_id varchar(64), source varchar(32) NOT NULL,
 model_id bigint NOT NULL, model_name varchar(128), protocol varchar(64), currency varchar(16),
 input_price decimal(28,12), output_price decimal(28,12), context_window int, output_limit int,
 input_tokens bigint, output_tokens bigint, cached_tokens bigint, reasoning_tokens bigint, usage_source varchar(24),
 cost decimal(28,12), status varchar(32) NOT NULL, error_code varchar(64),
 usage_date date NOT NULL, started_at datetime(3) NOT NULL, ended_at datetime(3),
 PRIMARY KEY (call_id), KEY idx_ai_call_tenant_date (tenant_id,usage_date), KEY idx_ai_call_request (tenant_id,request_id),
 KEY idx_ai_call_ab (tenant_id,ab_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_model_call_daily (
 tenant_id varchar(20) NOT NULL, user_id bigint NOT NULL, model_id bigint NOT NULL, currency varchar(16) NOT NULL, usage_date date NOT NULL,
 input_tokens bigint NOT NULL DEFAULT 0, output_tokens bigint NOT NULL DEFAULT 0, request_count bigint NOT NULL DEFAULT 0,
 unknown_calls bigint NOT NULL DEFAULT 0, known_cost decimal(28,12) NOT NULL DEFAULT 0,
 PRIMARY KEY (tenant_id,user_id,model_id,currency,usage_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_budget (
 budget_id varchar(64) NOT NULL, policy_id varchar(64) NOT NULL, tenant_id varchar(20) NOT NULL, currency varchar(16) NOT NULL,
 scope_type varchar(24) NOT NULL, scope_id varchar(64) NOT NULL DEFAULT '', period_start date NOT NULL,
 limit_amount decimal(28,12) NOT NULL, spent_amount decimal(28,12) NOT NULL DEFAULT 0, held_amount decimal(28,12) NOT NULL DEFAULT 0,
 enabled tinyint NOT NULL DEFAULT 0, blocked tinyint NOT NULL DEFAULT 0, revision bigint NOT NULL DEFAULT 0, updated_by bigint NOT NULL,
 PRIMARY KEY (budget_id), UNIQUE KEY uk_ai_budget_scope (tenant_id,currency,scope_type,scope_id,period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_budget_reservation (
 call_id varchar(64) NOT NULL, budget_id varchar(64) NOT NULL, tenant_id varchar(20) NOT NULL,
 amount decimal(28,12) NOT NULL, status varchar(24) NOT NULL,
 PRIMARY KEY (call_id,budget_id), KEY idx_ai_reservation_tenant (tenant_id,budget_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_model_call_adjustment (
 adjustment_id varchar(64) NOT NULL, call_id varchar(64) NOT NULL, tenant_id varchar(20) NOT NULL,
 currency varchar(16) NOT NULL, delta_amount decimal(28,12) NOT NULL, confirmed_amount decimal(28,12) NOT NULL,
 reason varchar(500) NOT NULL, operator_id bigint NOT NULL, created_at datetime(3) NOT NULL,
 input_tokens bigint, output_tokens bigint, cached_tokens bigint, reasoning_tokens bigint, usage_source varchar(24),
 PRIMARY KEY (adjustment_id), KEY idx_ai_adjustment_call (tenant_id,call_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_execution (
 execution_id varchar(64) NOT NULL, tenant_id varchar(20) NOT NULL, user_id bigint NOT NULL, kind varchar(16) NOT NULL,
 source_id varchar(64) NOT NULL, session_id bigint, status varchar(32) NOT NULL, owner_id varchar(64), generation bigint NOT NULL DEFAULT 0,
 lease_until datetime(3), deadline_at datetime(3) NOT NULL, wake_at datetime(3), cancel_requested tinyint NOT NULL DEFAULT 0,
 payload longtext NOT NULL, checkpoint longtext, partial_content longtext, message_id bigint, error_code varchar(64),
 created_at datetime(3) NOT NULL, updated_at datetime(3) NOT NULL,
 PRIMARY KEY (execution_id), KEY idx_ai_execution_due (kind,status,wake_at), KEY idx_ai_execution_owner (owner_id,status,lease_until),
 KEY idx_ai_execution_user (tenant_id,user_id,created_at), KEY idx_ai_execution_source (tenant_id,kind,source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_budget_policy (
 policy_id varchar(64) NOT NULL, tenant_id varchar(20) NOT NULL, currency varchar(16) NOT NULL,
 scope_type varchar(24) NOT NULL, scope_id varchar(64) NOT NULL, limit_amount decimal(28,12) NOT NULL,
 enabled tinyint NOT NULL DEFAULT 0, updated_by bigint NOT NULL, revision bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (policy_id), UNIQUE KEY uk_ai_budget_policy (tenant_id,currency,scope_type,scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_budget_policy_audit (
 audit_id varchar(64) NOT NULL, tenant_id varchar(20) NOT NULL, policy_id varchar(64) NOT NULL,
 limit_amount decimal(28,12) NOT NULL, enabled tinyint NOT NULL, operator_id bigint NOT NULL, created_at datetime(3) NOT NULL,
 PRIMARY KEY (audit_id), KEY idx_ai_budget_audit (tenant_id,policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_ab_run (
 run_id varchar(64) NOT NULL, tenant_id varchar(20) NOT NULL, user_id bigint NOT NULL, definition_id bigint NOT NULL,
 execution_id varchar(64) NOT NULL, start_key varchar(80) NOT NULL, test_name varchar(128), snapshot longtext NOT NULL, created_at datetime(3) NOT NULL,
 PRIMARY KEY (run_id), UNIQUE KEY uk_ai_ab_start (tenant_id,user_id,start_key), KEY idx_ai_ab_definition (tenant_id,definition_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_ab_run_item (
 item_id varchar(64) NOT NULL, run_id varchar(64) NOT NULL, tenant_id varchar(20) NOT NULL, question_index int NOT NULL,
 model_slot varchar(1) NOT NULL, model_id bigint NOT NULL, model_name varchar(128), question longtext NOT NULL,
 status varchar(32) NOT NULL, attempt int NOT NULL DEFAULT 0, generation bigint, response longtext, error_code varchar(64),
 latency_ms bigint, score int, updated_at datetime(3) NOT NULL,
 PRIMARY KEY (item_id), UNIQUE KEY uk_ai_ab_question_slot (run_id,question_index,model_slot), KEY idx_ai_ab_items (tenant_id,run_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_flow_attempt (
 attempt_id varchar(64) NOT NULL, tenant_id varchar(20) NOT NULL, execution_id varchar(64) NOT NULL, instance_id bigint NOT NULL,
 generation bigint NOT NULL, node_id varchar(128) NOT NULL, execution_key varchar(255) NOT NULL, replay_policy varchar(24) NOT NULL,
 status varchar(32) NOT NULL, started_at datetime(3) NOT NULL, ended_at datetime(3), error_code varchar(64),
 PRIMARY KEY (attempt_id), KEY idx_ai_flow_attempt (tenant_id,instance_id,node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_flow_retry_link (
 tenant_id varchar(20) NOT NULL, instance_id bigint NOT NULL, retry_of_instance_id bigint NOT NULL, created_at datetime(3) NOT NULL,
 PRIMARY KEY (tenant_id,instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
