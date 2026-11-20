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
    accountStatus: "active",
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

test("Normal user can create own profile with badgeType=none and active status, cannot self-assign badges or restriction", async () => {
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

  // Escalating badgeType or changing accountStatus via update fails
  await assertFails(
    aliceDb.collection("community_users").doc(ALICE_UID).update({
      badgeType: "blue",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("community_users").doc(ALICE_UID).update({
      accountStatus: "banned",
      restricted: true,
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

test("Verified official admin can bootstrap red_admin profile, grant/revoke blue badge, suspend/ban/unban users, and cannot be demoted or restricted", async () => {
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

  // Admin grants and revokes blue badge for Alice
  await assertSucceeds(
    adminDb.collection("community_users").doc(ALICE_UID).update({
      badgeType: "blue",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(
    adminDb.collection("community_users").doc(ALICE_UID).update({
      badgeType: "none",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Admin suspends Alice (accountStatus='suspended' + restricted=true)
  await assertSucceeds(
    adminDb.collection("community_users").doc(ALICE_UID).update({
      accountStatus: "suspended",
      restricted: true,
      restrictionReason: "Temporary cooldown",
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Suspended Alice cannot create a post
  const now = new Date(Date.now() - 200);
  await assertFails(
    aliceDb.collection("community_posts").doc("post_blocked").set({
      authorUid: ALICE_UID,
      authorName: "Alice",
      authorPhotoUrl: "https://pockethostbd.github.io/drive-one/picture/mybookscommunity-userprofilepicture.png",
      authorBadge: "none",
      content: "Trying to post while suspended",
      visibility: "visible",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin restores Alice to active
  await assertSucceeds(
    adminDb.collection("community_users").doc(ALICE_UID).update({
      accountStatus: "active",
      restricted: false,
      restrictionReason: "",
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Admin cannot revoke own red_admin status or ban self
  await assertFails(
    adminDb.collection("community_users").doc(ADMIN_UID).update({
      badgeType: "none",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    adminDb.collection("community_users").doc(ADMIN_UID).update({
      accountStatus: "banned",
      restricted: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Posts enforce authorBadge parity, valid Google Drive links with bookTitle, owner editing, and moderation visibility", async () => {
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
      bookTitle: "Bad File",
      visibility: "visible",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid post with Google Drive link and bookTitle succeeds
  await assertSucceeds(
    aliceDb.collection("community_posts").doc("post_1").set({
      authorUid: ALICE_UID,
      authorName: "Alice",
      authorPhotoUrl: "https://pockethostbd.github.io/drive-one/picture/mybookscommunity-userprofilepicture.png",
      authorBadge: "none",
      content: "Sharing my reading notes 📚",
      driveLink: "https://drive.google.com/file/d/123456/view",
      bookTitle: "Gitanjali Notes",
      visibility: "visible",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice can edit her own post content and bookTitle
  await assertSucceeds(
    aliceDb.collection("community_posts").doc("post_1").update({
      content: "Updated notes 📚",
      bookTitle: "Gitanjali Study Edition",
      updatedAt: new Date(Date.now() - 600),
    })
  );

  // Filtered visible query succeeds
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
      updatedAt: new Date(Date.now() - 400),
    })
  );
});

test("Comments and threaded replies enforce active account status, valid parent comments, and owner/admin moderation", async () => {
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
  await assertSucceeds(bobDb.collection("community_users").doc(BOB_UID).set(makeUserPayload(BOB_UID, "none")));
  await assertSucceeds(adminDb.collection("community_users").doc(ADMIN_UID).set(makeUserPayload(ADMIN_UID, "red_admin")));

  const now = new Date(Date.now() - 1000);
  await assertSucceeds(
    aliceDb.collection("community_posts").doc("post_c1").set({
      authorUid: ALICE_UID,
      authorName: "Alice",
      authorPhotoUrl: "https://pockethostbd.github.io/drive-one/picture/mybookscommunity-userprofilepicture.png",
      authorBadge: "none",
      content: "Favorite novel this year?",
      visibility: "visible",
      createdAt: now,
      updatedAt: now,
    })
  );

  const commentsRef = aliceDb.collection("community_posts").doc("post_c1").collection("comments");
  const bobCommentsRef = bobDb.collection("community_posts").doc("post_c1").collection("comments");

  // Bob creates top-level comment
  await assertSucceeds(
    bobCommentsRef.doc("c_1").set({
      postId: "post_c1",
      authorUid: BOB_UID,
      authorName: "Bob",
      authorPhotoUrl: "https://pockethostbd.github.io/drive-one/picture/mybookscommunity-userprofilepicture.png",
      authorBadge: "none",
      parentCommentId: "",
      content: "Himu Dwitiyo Prohor!",
      visibility: "visible",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice replies to Bob's comment
  await assertSucceeds(
    commentsRef.doc("c_2").set({
      postId: "post_c1",
      authorUid: ALICE_UID,
      authorName: "Alice",
      authorPhotoUrl: "https://pockethostbd.github.io/drive-one/picture/mybookscommunity-userprofilepicture.png",
      authorBadge: "none",
      parentCommentId: "c_1",
      replyToAuthorName: "Bob",
      content: "Great choice!",
      visibility: "visible",
      createdAt: new Date(Date.now() - 800),
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Reply to non-existent parentCommentId fails
  await assertFails(
    commentsRef.doc("c_bad").set({
      postId: "post_c1",
      authorUid: ALICE_UID,
      authorName: "Alice",
      authorPhotoUrl: "https://pockethostbd.github.io/drive-one/picture/mybookscommunity-userprofilepicture.png",
      authorBadge: "none",
      parentCommentId: "missing_parent",
      content: "Orphan reply",
      visibility: "visible",
      createdAt: new Date(Date.now() - 700),
      updatedAt: new Date(Date.now() - 700),
    })
  );

  // Alice cannot delete Bob's comment; Admin can hide Bob's comment
  await assertFails(commentsRef.doc("c_1").delete());
  await assertSucceeds(
    adminDb.collection("community_posts").doc("post_c1").collection("comments").doc("c_1").update({
      visibility: "hidden",
      moderationNote: "Moderated",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Banned user cannot comment
  await assertSucceeds(
    adminDb.collection("community_users").doc(BOB_UID).update({
      accountStatus: "banned",
      restricted: true,
      restrictionReason: "Violation",
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertFails(
    bobCommentsRef.doc("c_banned").set({
      postId: "post_c1",
      authorUid: BOB_UID,
      authorName: "Bob",
      authorPhotoUrl: "https://pockethostbd.github.io/drive-one/picture/mybookscommunity-userprofilepicture.png",
      authorBadge: "none",
      parentCommentId: "",
      content: "Blocked comment",
      visibility: "visible",
      createdAt: new Date(Date.now() - 300),
      updatedAt: new Date(Date.now() - 300),
    })
  );
});

test("Reports support post, comment, and profile types, isolate reporter privacy, and allow admin moderation + audit logs", async () => {
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
  await assertSucceeds(
    bobDb.collection("community_reports").doc("comment_c_1_bob_user_2").set({
      reporterUid: BOB_UID,
      targetType: "comment",
      targetId: "c_1",
      parentPostId: "post_1",
      reason: "insults",
      details: "Rude reply",
      status: "pending",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("community_reports").doc("profile_alice_user_1_bob_user_2").set({
      reporterUid: BOB_UID,
      targetType: "profile",
      targetId: ALICE_UID,
      reason: "impersonation",
      details: "Suspicious profile",
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
