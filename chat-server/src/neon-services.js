import crypto from 'node:crypto';
import { S3Client, PutObjectCommand } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { createRemoteJWKSet, jwtVerify } from 'jose';

let jwks;
let cachedS3;

function storageClient() {
  if (cachedS3) return cachedS3;
  if (!process.env.AWS_ACCESS_KEY_ID || !process.env.AWS_SECRET_ACCESS_KEY || !process.env.AWS_ENDPOINT_URL_S3 || !process.env.AWS_REGION) return null;
  cachedS3 = new S3Client({ region: process.env.AWS_REGION, endpoint: process.env.AWS_ENDPOINT_URL_S3, forcePathStyle: true });
  return cachedS3;
}

export function neonServiceStatus() {
  return {
    postgres: Boolean(process.env.DATABASE_URL),
    auth: Boolean(process.env.NEON_AUTH_BASE_URL && process.env.NEON_AUTH_JWKS_URL),
    objectStorage: Boolean(storageClient()),
    functions: Boolean(process.env.NEON_FUNCTION_HOST_SUFFIX && process.env.NEON_BRANCH_ID),
    realtime: Boolean(process.env.NEON_REALTIME_URL),
  };
}

export function neonHelloFunctionUrl() {
  if (!process.env.NEON_FUNCTION_HOST_SUFFIX || !process.env.NEON_BRANCH_ID) return null;
  return `https://${process.env.NEON_BRANCH_ID}-hello${process.env.NEON_FUNCTION_HOST_SUFFIX}/`;
}

export async function verifyNeonAuthToken(token) {
  const jwksUrl = process.env.NEON_AUTH_JWKS_URL?.trim();
  const authBaseUrl = process.env.NEON_AUTH_BASE_URL?.trim();
  if (!jwksUrl || !authBaseUrl || !token) return null;
  try {
    jwks ||= createRemoteJWKSet(new URL(jwksUrl));
    const { payload } = await jwtVerify(token, jwks, { issuer: new URL(authBaseUrl).origin });
    return payload.sub ? payload : null;
  } catch {
    return null;
  }
}

export async function createAssetUploadUrl({ userId, contentType = 'application/octet-stream' }) {
  const s3 = storageClient();
  if (!s3) throw new Error('neon_object_storage_not_configured');
  const safeType = String(contentType).trim().slice(0, 100) || 'application/octet-stream';
  const key = `chat/${userId}/${crypto.randomUUID()}`;
  const command = new PutObjectCommand({
    Bucket: process.env.NEON_OBJECT_STORAGE_BUCKET || 'assets',
    Key: key,
    ContentType: safeType,
  });
  const uploadUrl = await getSignedUrl(s3, command, { expiresIn: 900 });
  return { bucket: process.env.NEON_OBJECT_STORAGE_BUCKET || 'assets', key, uploadUrl, expiresIn: 900 };
}

export function neonRealtimeConfig() {
  return process.env.NEON_REALTIME_URL
    ? { url: process.env.NEON_REALTIME_URL, enabled: true }
    : { url: null, enabled: false };
}
