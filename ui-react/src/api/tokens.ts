import { message } from "antd";

interface TokenUsage {
  tokensUsed: number;
  monthlyLimit: number;
  monthYear: string;
}

export async function getTokenUsage(token: string): Promise<TokenUsage | null> {
  try {
    const response = await fetch(`${process.env.APP_BASE_URL}/api/tokens`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    if (!response.ok) {
      throw new Error("Failed to fetch token usage");
    }
    return await response.json();
  } catch (error) {
    message.error("获取使用量失败");
    return null;
  }
}
