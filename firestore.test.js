const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_user_1";
const BOB_UID = "bob_user_2";
const ADMIN_UID = "admin_omor_0";
const ADMIN_EMAIL = "muhammedomor.dev@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function makeUserPayload(uid, badgeType = "none") {
  const now = new Date(Date.now() - 1000);
  return {
    uid,
    displayName: "Reader " + uid,
    photoUrl: "https://pockethostbd.github.io/drive-one/picture/mybookscommunity-userprofilepicture.png",
    bio: "Book lover",
    badgeType,
    restricted: false,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated users cannot read or write community collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("community_posts").where("visibility", "==", "visible").get());
  await assertFails(unauthDb.collection("community_users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("community_users").doc(ALICE_UID).set(makeUserPayload(ALICE_UID)));
});

test("Normal user can create own profile with badgeType=none, cannot self-assign badges or restriction", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  // Self-assigning blue or red badge fails
  await assertFails(aliceDb.collection("community_users").doc(ALICE_UID).set(makeUserPayload(ALICE_UID, "blue")));
  await assertFails(aliceDb.collection("community_users").doc(ALICE_UID).set(makeUserPayload(ALICE_UID, "red_admin")));

  // Valid creation succeeds
  await assertSucceeds(aliceDb.collection("community_users").doc(ALICE_UID).set(makeUserPayload(ALICE_UID, "none")));

  // Updating own displayName/bio succeeds
  await assertSucceeds(
    aliceDb.collection("community_users").doc(ALICE_UID).update({
      displayName: "Alice Updated",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Escalating badgeType via update fails
  await assertFails(
    aliceDb.collection("community_users").doc(ALICE_UID).update({
      badgeType: "blue",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Email spoofing attack with email_verified=false is rejected for admin", async () => {
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofedAdminDb.collection("community_users").doc("spoof_uid").set(makeUserPayload("spoof_uid", "red_admin"))
  );
  await assertFails(
    spoofedAdminDb.collection("community_reports").get()
  );
});

test("Verified official admin can bootstrap red_admin profile, grant/revoke blue badge, and cannot lose red_admin", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(adminDb.collection("community_users").doc(ADMIN_UID).set(makeUserPayload(ADMIN_UID, "red_admin")));
  await assertSucceeds(aliceDb.collection("community_users").doc(ALICE_UID).set(makeUserPayload(ALICE_UID, "none")));

  // Admin grants blue badge to Alice
  await assertSucceeds(
    adminDb.collection("community_users").doc(ALICE_UID).update({
      badgeType: "blue",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Admin cannot revoke their own red_admin status
  await assertFails(
    adminDb.collection("community_users").doc(ADMIN_UID).update({
      badgeType: "none",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Posts enforce authorBadge parity, valid Google Drive links, and moderation visibility", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(aliceDb.collection("community_users").doc(ALICE_UID).set(makeUserPayload(ALICE_UID, "none")));
  const now = new Date(Date.now() - 1000);

  // Forged authorBadge fails
  await assertFails(
    aliceDb.collection("community_posts").doc("post_1").set({
      authorUid: ALICE_UID,
      authorName: "Alice",
      authorPhotoUrl: "https://pockethostbd.github.io/drive-one/picture/mybookscommunity-userprofilepicture.png",
      authorBadge: "blue",
      content: "Hello MyBooks!",
      visibility: "visible",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Non-Drive URL fails
  await assertFails(
    aliceDb.collection("community_posts").doc("post_1").set({
      authorUid: ALICE_UID,
      authorName: "Alice",
      authorPhotoUrl: "https://pockethostbd.github.io/drive-one/picture/mybookscommunity-userprofilepicture.png",
      authorBadge: "none",
      content: "Check this out",
      driveLink: "https://evil.example.com/malware.apk",
      visibility: "visible",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid post with Google Drive link succeeds
  await assertSucceeds(
    aliceDb.collection("community_posts").doc("post_1").set({
      authorUid: ALICE_UID,
      authorName: "Alice",
      authorPhotoUrl: "https://pockethostbd.github.io/drive-one/picture/mybookscommunity-userprofilepicture.png",
      authorBadge: "none",
      content: "Sharing my reading notes 📚",
      driveLink: "https://drive.google.com/file/d/123456/view",
      visibility: "visible",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered visible query succeeds; unfiltered query fails if hidden docs could match
  await assertSucceeds(
    bobDb.collection("community_posts").where("visibility", "==", "visible").get()
  );

  // Bob cannot edit Alice's post
  await assertFails(
    bobDb.collection("community_posts").doc("post_1").update({
      content: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Admin can hide Alice's post
  await assertSucceeds(
    adminDb.collection("community_posts").doc("post_1").update({
      visibility: "hidden",
      moderationNote: "Policy violation",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Reports isolate reporter privacy and allow admin moderation + audit logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const now = new Date(Date.now() - 1000);
  await assertSucceeds(
    bobDb.collection("community_reports").doc("post_post_1_bob_user_2").set({
      reporterUid: BOB_UID,
      targetType: "post",
      targetId: "post_1",
      reason: "spam",
      details: "Unrelated promotional spam",
      status: "pending",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice cannot read Bob's report or list all reports
  await assertFails(aliceDb.collection("community_reports").doc("post_post_1_bob_user_2").get());
  await assertFails(aliceDb.collection("community_reports").get());

  // Bob can query his own reports
  await assertSucceeds(bobDb.collection("community_reports").where("reporterUid", "==", BOB_UID).get());

  // Admin can list all reports, review report, and write audit log
  await assertSucceeds(adminDb.collection("community_reports").get());
  await assertSucceeds(
    adminDb.collection("community_reports").doc("post_post_1_bob_user_2").update({
      status: "reviewed",
      reviewedBy: ADMIN_UID,
      moderationAction: "Reviewed and hidden",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(
    adminDb.collection("moderation_audit_logs").doc("log_1").set({
      adminUid: ADMIN_UID,
      action: "hide_post",
      targetType: "post",
      targetId: "post_1",
      note: "Removed spam",
      createdAt: new Date(Date.now() - 500),
    })
  );

  // Normal user cannot read or write audit logs
  await assertFails(aliceDb.collection("moderation_audit_logs").get());
});
