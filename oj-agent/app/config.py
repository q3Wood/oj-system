from pydantic_settings import BaseSettings, SettingsConfigDict


class Config(BaseSettings):
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com"
    
    # 可选：用于 ChromaDB 向量化的 OpenAI 兼容接口（推荐填硅基流动免费 Key）
    embedding_api_key: str = ""
    embedding_base_url: str = "https://api.siliconflow.cn/v1"
    embedding_model: str = "BAAI/bge-m3"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )


settings = Config()