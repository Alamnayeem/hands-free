enum ActionType {
  tap,
  doubleTap,
  longPress,
  scrollUp,
  scrollDown,
  back,
  home,
  recents,
  notifications,
  quickSettings,
  screenshot,
  volumeUp,
  volumeDown,
  lockScreen,
  typeText,
  openApp,
}

class HandsFreeCommand {
  final ActionType type;
  final String englishLabel;
  final String bengaliLabel;
  final List<String> englishTriggers;
  final List<String> bengaliTriggers;
  final String? appName; // Optional for openApp type
  final String? textToType; // Optional for typeText type

  HandsFreeCommand({
    required this.type,
    required this.englishLabel,
    required this.bengaliLabel,
    required this.englishTriggers,
    required this.bengaliTriggers,
    this.appName,
    this.textToType,
  });

  bool matches(String verbalInput) {
    final cleaned = verbalInput.toLowerCase().trim();
    
    // Check direct English triggers
    for (final trigger in englishTriggers) {
      if (cleaned == trigger.toLowerCase() || cleaned.contains(trigger.toLowerCase())) {
        return true;
      }
    }

    // Check direct Bengali triggers
    for (final trigger in bengaliTriggers) {
      if (cleaned == trigger.trim() || cleaned.contains(trigger.trim())) {
        return true;
      }
    }

    // Special match for dynamic commands: e.g., "type hello" or "টাইপ হ্যালো"
    if (type == ActionType.typeText) {
      if (cleaned.startsWith("type ") || cleaned.startsWith("টাইপ ")) {
        return true;
      }
    }

    // Special match for dynamic apps: e.g., "open whatsapp" or "হোয়াটসঅ্যাপ খোল"
    if (type == ActionType.openApp) {
      if (cleaned.startsWith("open ") || cleaned.endsWith(" খোল") || cleaned.endsWith(" খোলো")) {
        return true;
      }
    }

    return false;
  }

  // Generate dynamic payload based on verbal input
  HandsFreeCommand withDynamicPayload(String verbalInput) {
    final cleaned = verbalInput.toLowerCase().trim();
    if (type == ActionType.typeText) {
      String typedText = "";
      if (cleaned.startsWith("type ")) {
        typedText = verbalInput.substring(5).trim();
      } else if (cleaned.startsWith("টাইপ ")) {
        typedText = verbalInput.substring(5).trim();
      }
      return HandsFreeCommand(
        type: type,
        englishLabel: englishLabel,
        bengaliLabel: bengaliLabel,
        englishTriggers: englishTriggers,
        bengaliTriggers: bengaliTriggers,
        textToType: typedText,
      );
    }

    if (type == ActionType.openApp) {
      String app = "";
      if (cleaned.startsWith("open ")) {
        app = verbalInput.substring(5).trim();
      } else if (cleaned.endsWith(" খোল")) {
        app = verbalInput.substring(0, verbalInput.length - 4).trim();
      } else if (cleaned.endsWith(" খোলো")) {
        app = verbalInput.substring(0, verbalInput.length - 5).trim();
      }
      return HandsFreeCommand(
        type: type,
        englishLabel: englishLabel,
        bengaliLabel: bengaliLabel,
        englishTriggers: englishTriggers,
        bengaliTriggers: bengaliTriggers,
        appName: app,
      );
    }

    return this;
  }

  static List<HandsFreeCommand> get defaultCommandsList => [
    HandsFreeCommand(
      type: ActionType.tap,
      englishLabel: "Click",
      bengaliLabel: "ক্লিক",
      englishTriggers: ["click", "tap", "select", "press"],
      bengaliTriggers: ["ক্লিক", "ট্যাপ", "সিলেক্ট", "চাপো"],
    ),
    HandsFreeCommand(
      type: ActionType.doubleTap,
      englishLabel: "Double Click",
      bengaliLabel: "ডাবল ক্লিক",
      englishTriggers: ["double click", "double tap"],
      bengaliTriggers: ["ডাবল ক্লিক", "দুইবার ক্লিক", "ডাবল ট্যাপ"],
    ),
    HandsFreeCommand(
      type: ActionType.longPress,
      englishLabel: "Long Press",
      bengaliLabel: "লং প্রেস",
      englishTriggers: ["long press", "hold"],
      bengaliTriggers: ["লং প্রেস", "চেপে ধরো", "হোল্ড"],
    ),
    HandsFreeCommand(
      type: ActionType.scrollUp,
      englishLabel: "Scroll Up",
      bengaliLabel: "স্ক্রল আপ",
      englishTriggers: ["scroll up", "go up", "up"],
      bengaliTriggers: ["স্ক্রল আপ", "উপরে যাও", "উপরে"],
    ),
    HandsFreeCommand(
      type: ActionType.scrollDown,
      englishLabel: "Scroll Down",
      bengaliLabel: "স্ক্রল ডাউন",
      englishTriggers: ["scroll down", "go down", "down"],
      bengaliTriggers: ["স্ক্রল ডাউন", "নিচে যাও", "নিচে"],
    ),
    HandsFreeCommand(
      type: ActionType.back,
      englishLabel: "Back",
      bengaliLabel: "ব্যাক",
      englishTriggers: ["back", "go back"],
      bengaliTriggers: ["ব্যাক", "পিছনে যাও", "পেছনে"],
    ),
    HandsFreeCommand(
      type: ActionType.home,
      englishLabel: "Home",
      bengaliLabel: "হোম",
      englishTriggers: ["home", "go home", "main screen"],
      bengaliTriggers: ["হোম", "হোমে যাও", "মূল স্ক্রিন"],
    ),
    HandsFreeCommand(
      type: ActionType.recents,
      englishLabel: "Recent Apps",
      bengaliLabel: "রিসেন্ট অ্যাপস",
      englishTriggers: ["recent apps", "recents", "overview"],
      bengaliTriggers: ["রিসেন্ট অ্যাপস", "রিসেন্ট", "সব অ্যাপ"],
    ),
    HandsFreeCommand(
      type: ActionType.notifications,
      englishLabel: "Notification Panel",
      bengaliLabel: "নোটিফিকেশন",
      englishTriggers: ["open notifications", "notifications", "show notifications"],
      bengaliTriggers: ["নোটিফিকেশন", "নোটিফিকেশন বার", "নোটিফিকেশন প্যানেল"],
    ),
    HandsFreeCommand(
      type: ActionType.quickSettings,
      englishLabel: "Quick Settings",
      bengaliLabel: "কুইক সেটিংস",
      englishTriggers: ["quick settings", "open shortcuts"],
      bengaliTriggers: ["কুইক সেটিংস", "শর্টকাট"],
    ),
    HandsFreeCommand(
      type: ActionType.screenshot,
      englishLabel: "Take Screenshot",
      bengaliLabel: "স্ক্রিনশট",
      englishTriggers: ["screenshot", "take screenshot", "capture screen"],
      bengaliTriggers: ["স্ক্রিনশট", "স্ক্রিনশট নাও", "ছবি তোলো"],
    ),
    HandsFreeCommand(
      type: ActionType.volumeUp,
      englishLabel: "Volume Up",
      bengaliLabel: "ভলিউম বাড়াও",
      englishTriggers: ["volume up", "louder", "increase volume"],
      bengaliTriggers: ["ভলিউম বাড়াও", "আওয়াজ বাড়াও", "ভলিউম আপ"],
    ),
    HandsFreeCommand(
      type: ActionType.volumeDown,
      englishLabel: "Volume Down",
      bengaliLabel: "ভলিউম কমাও",
      englishTriggers: ["volume down", "quieter", "decrease volume"],
      bengaliTriggers: ["ভলিউম কমাও", "আওয়াজ কমাও", "ভলিউম ডাউন"],
    ),
    HandsFreeCommand(
      type: ActionType.lockScreen,
      englishLabel: "Lock Screen",
      bengaliLabel: "লক স্ক্রিন",
      englishTriggers: ["lock screen", "lock phone", "lock"],
      bengaliTriggers: ["লক", "লক স্ক্রিন", "মোবাইল লক করো", "লক করো"],
    ),
    HandsFreeCommand(
      type: ActionType.typeText,
      englishLabel: "Type text",
      bengaliLabel: "টাইপ করুন",
      englishTriggers: ["type"],
      bengaliTriggers: ["টাইপ"],
    ),
    HandsFreeCommand(
      type: ActionType.openApp,
      englishLabel: "Open App",
      bengaliLabel: "অ্যাপস খুলুন",
      englishTriggers: ["open"],
      bengaliTriggers: ["খোল", "খোলো"],
    ),
  ];
}
