from pydantic_settings import BaseSettings, SettingsConfigDict

class Config(BaseSettings):
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )

settings = Config()