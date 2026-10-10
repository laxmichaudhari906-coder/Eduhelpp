const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.sendMaskedNotification = functions.firestore
  .document("pairs/{pairId}/messages/{messageId}")
  .onCreate(async (snap, context) => {
    const message = snap.data();
    const { pairId, messageId } = context.params;

    let recipientUid = null;
    const pairSnap = await admin.firestore().collection("pairs").doc(pairId).get();
    if (pairSnap.exists) {
      const users = pairSnap.data().users || [];
      recipientUid = users.find(uid => uid !== message.senderId);
    }

    if (!recipientUid) return null;

    const userSnap = await admin.firestore().collection("users").doc(recipientUid).get();
    if (!userSnap.exists) return null;
    const fcmToken = userSnap.data().fcmToken;
    if (!fcmToken) return null;

    const academicAlerts = [
      "New quiz questions available for your study cohort.",
      "Review your weekly flashcard progress.",
      "Cardiology lecture notes updated.",
      "Cohort practice quiz starts in 10 minutes."
    ];
    const maskedBody = academicAlerts[Math.floor(Math.random() * academicAlerts.length)];

    const messagePayload = {
      token: fcmToken,
      data: {
        title: "Edu Help",
        body: maskedBody,
        pairId: pairId,
        roomId: pairId,
        messageId: messageId,
        clickAction: "FLIGHT_CAMOUFLAGE_LAUNCH"
      },
      android: {
        priority: "high"
      }
    };

    return admin.messaging().send(messagePayload);
  });
