from app.rag.store import knowledge_collection

def retrieve_oj_knowledge(user_code: str) -> str | None:
    """在通用竞赛避坑库中打捞代码可能涉及的底层陷阱"""
    retrieved_pieces = []

    try:
        # 用学生的代码核心摘要（前 300 字）做向量搜索
        pitfall_results = knowledge_collection.query(
            query_texts=[user_code[:300]],
            n_results=1,
            where={"type": "pitfall"}
        )
        
        #  严谨类型守卫：先存为局部变量，彻底消除 Pylance 红线！
        docs = pitfall_results.get("documents")
        if docs and len(docs) > 0 and len(docs[0]) > 0:
            best_doc = docs[0][0]
            retrieved_pieces.append(best_doc)
            
    except Exception as e:
        print(f"⚠️ 避坑条款检索异常: {e}")

    # 平滑降级
    if not retrieved_pieces:
        return None

    return "\n\n".join(retrieved_pieces)