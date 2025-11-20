import { VercelRequest, VercelResponse } from '@vercel/node';
import { app } from '../../frontend/dist/frontend/server/main.mjs';

const ssrApp = app();

export default async function handler(
  req: VercelRequest,
  res: VercelResponse
): Promise<void> {
  // Create a mock response object that Vercel can handle
  const expressRes = {
    status: (code: number) => ({
      send: (html: string) => {
        res.status(code).send(html);
      },
      json: (data: any) => {
        res.status(code).json(data);
      },
    }),
    send: (html: string) => {
      res.status(200).send(html);
    },
    json: (data: any) => {
      res.status(200).json(data);
    },
    set: (key: string, value: string) => {
      res.setHeader(key, value);
    },
  };

  // Delegate to Express SSR app
  ssrApp(req, expressRes);
}
