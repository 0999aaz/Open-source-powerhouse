import base64
import binascii
import json

from app.core.exceptions import AppError


def encode_cursor(rank: int, repo_id: int) -> str:
    payload = json.dumps({"r": rank, "i": repo_id}, separators=(",", ":")).encode(
        "utf-8"
    )
    return base64.urlsafe_b64encode(payload).decode("ascii").rstrip("=")


def decode_cursor(cursor: str) -> tuple[int, int]:
    if not cursor or len(cursor) > 128:
        raise AppError(400, "INVALID_CURSOR", "分页游标格式无效")

    padded = cursor + "=" * (-len(cursor) % 4)
    try:
        raw = base64.urlsafe_b64decode(padded.encode("ascii"))
        data = json.loads(raw.decode("utf-8"))
        rank = int(data["r"])
        repo_id = int(data["i"])
        if rank < 1 or repo_id < 1:
            raise ValueError("Non-positive cursor values")
        return rank, repo_id
    except (binascii.Error, UnicodeDecodeError, json.JSONDecodeError, KeyError, TypeError, ValueError) as exc:
        raise AppError(400, "INVALID_CURSOR", "分页游标无效或已损坏") from exc
