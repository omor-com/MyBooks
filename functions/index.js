const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { beforeUserSignedIn } = require("firebase-functions/v2/identity");
const admin = require("firebase-admin");

admin.initializeApp();

const OFFICIAL_ADMIN_EMAIL = "muhammedomor.dev@gmail.com";
const DATABASE_ID = "ai-studio-android-mybooks-9953f4b2-6f3f-4c3c-b284-98d0af7e8dab";

/**
 * Auth blocking trigger: stamps custom claim `{ admin: true }` when the official
 * verified administrator signs in with Google, and ensures `/admins/{uid}` exists.
 */
exports.assignOfficialAdminClaim = beforeUserSignedIn(async (event) => {
  const user = event.data;
  if (!user) return;

  const isOfficialAdmin =
    user.emailVerified === true && user.email === OFFICIAL_ADMIN_EMAIL;

  if (isOfficialAdmin) {
    const db = admin.firestore();
    try {
      await db.collection("admins").doc(user.uid).set(
        {
          uid: user.uid,
          email: OFFICIAL_ADMIN_EMAIL,
          role: "official_admin",
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
    } catch (err) {
      console.warn("Unable to write /admins doc during auth trigger:", err);
    }

    return {
      customClaims: {
        admin: true,
      },
    };
  }
  return {};
});

/**
 * Keeps author badge & display name synced across visible posts whenever a user's
 * badgeType is updated by moderation.
 */
exports.syncAuthorBadgeToPosts = onDocumentWritten(
  {
    document: "community_users/{uid}",
    database: DATABASE_ID,
  },
  async (event) => {
    const after = event.data?.after?.data();
    const before = event.data?.before?.data();
    if (!after) return;

    if (
      before &&
      before.badgeType === after.badgeType &&
      before.displayName === after.displayName
    ) {
      return;
    }

    const db = admin.firestore();
    db.settings({ databaseId: DATABASE_ID });
    const postsSnap = await db
      .collection("community_posts")
      .where("authorUid", "==", event.params.uid)
      .limit(200)
      .get();

    if (postsSnap.empty) return;
    const batch = db.batch();
    postsSnap.docs.forEach((doc) => {
      batch.update(doc.ref, {
        authorBadge: after.badgeType || "none",
        authorName: after.displayName || "Reader",
      });
    });
    await batch.commit();
  }
);
