-- Extensions
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_jieba;

-- Text search configuration for Chinese.
DO $$
DECLARE
  parser_name text;
BEGIN
  IF EXISTS (SELECT 1 FROM pg_ts_parser WHERE prsname = 'jieba') THEN
    parser_name := 'jieba';
  ELSIF EXISTS (SELECT 1 FROM pg_ts_parser WHERE prsname = 'pg_jieba') THEN
    parser_name := 'pg_jieba';
  END IF;

  IF parser_name IS NULL THEN
    RAISE EXCEPTION 'pg_jieba parser not found';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'jieba_cfg') THEN
    EXECUTE format('CREATE TEXT SEARCH CONFIGURATION jieba_cfg (PARSER = %I)', parser_name);
    ALTER TEXT SEARCH CONFIGURATION jieba_cfg
      ADD MAPPING FOR n,v,a,i,e,l,t,d,p,ns,nz,vn,eng WITH simple;
  END IF;
END $$;

-- Note:
-- If you maintain a user dictionary, mount it and load it after init.
-- Example (run once): SELECT jieba_load_userdict('/path/to/jieba_user.dict');
