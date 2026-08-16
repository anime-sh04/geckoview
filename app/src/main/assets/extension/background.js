// Background script for the GeckoBrowser example extension.
//
// This demonstrates the two-way messaging path:
//
//   Android app  <-- port -->  background.js  <-- runtime.sendMessage -->  content.js
//
// "browser" (the string passed to connectNative) is the native-app id the
// Android side registers with WebExtension.setMessageDelegate(delegate, "browser").
// It is GeckoView's own messaging mechanism (no external native-messaging
// host executable is involved, unlike desktop Firefox's nativeMessaging).

console.log("[GeckoBrowser extension] background script loaded");

const nativePort = browser.runtime.connectNative("browser");

// Messages sent from the Android app via Port.postMessage(...) arrive here.
nativePort.onMessage.addListener((message) => {
  console.log("[GeckoBrowser extension] message from app:", message);

  if (message && message.type === "START") {
    // Example acknowledgement back to the app.
    nativePort.postMessage({ type: "PAGE_READY", source: "background" });
  }
});

nativePort.onDisconnect.addListener(() => {
  console.log("[GeckoBrowser extension] native port disconnected");
});

// Messages sent from content scripts (via browser.runtime.sendMessage) arrive
// here and are forwarded on to the Android app over the native port.
browser.runtime.onMessage.addListener((message, sender) => {
  console.log("[GeckoBrowser extension] message from content script:", message);

  nativePort.postMessage({
    ...message,
    tabUrl: sender.tab ? sender.tab.url : undefined,
  });
});
