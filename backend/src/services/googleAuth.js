/**
 * Google OAuth 2.0 token verification service.
 * Verifies an Android-issued Google ID token and returns the user's profile.
 */

const { OAuth2Client } = require('google-auth-library');

const client = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

/**
 * Verify a Google ID token from the Android Firebase SDK.
 * @param {string} idToken - The raw ID token string from Firebase Auth
 * @returns {{ sub: string, email: string, name: string, picture: string|undefined }}
 */
async function verifyGoogleToken(idToken) {
  const ticket = await client.verifyIdToken({
    idToken,
    audience: process.env.GOOGLE_CLIENT_ID,
  });

  const payload = ticket.getPayload();

  return {
    sub: payload.sub,           // unique stable Google user ID
    email: payload.email,
    name: payload.name ?? payload.email,
    picture: payload.picture,
  };
}

module.exports = { verifyGoogleToken };
