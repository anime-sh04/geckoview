// Content script injected into https://10.11.111.11/* by GeckoView.
//
// This is intentionally minimal: it only proves the content-script
// injection and messaging pipeline works. Replace this with your own
// DOM interaction logic.

console.log("GeckoView extension content script loaded");

browser.runtime.sendMessage({
  type: "PAGE_READY",
  url: window.location.href,
  title: document.title,
});
