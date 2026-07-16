import traceback
from fastapi import APIRouter, HTTPException, status
from app.schemas.requests import RAGQueryRequest
from app.schemas.responses import ErrorResponse

router = APIRouter(prefix="/rag", tags=["RAG"])


@router.post(
    "/query",
    status_code=status.HTTP_200_OK,
    responses={500: {"model": ErrorResponse}},
    summary="Query RAG compliance agent"
)
def query_rag_compliance(request: RAGQueryRequest):
    """Runs the RAG Copilot Agent to search regulatory standards and historical
    incident precedents. May take 2-8 seconds when using live Gemini API."""
    try:
        from app.core.agents.rag_agent import RAGAgent
        agent = RAGAgent()
        result = agent.run(request.model_dump())
        return result
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"RAG query failed: {str(e)}"
        )
