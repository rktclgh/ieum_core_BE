ALTER TABLE ai_question_tasks
    ADD CONSTRAINT ck_ai_question_tasks_embedding_model
    CHECK (embedding_model IS NULL OR embedding_model = 'gemini-embedding-2') NOT VALID;
