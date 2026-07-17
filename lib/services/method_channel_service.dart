import 'package:flutter/services.dart';

class MethodChannelService {
  static const MethodChannel _channel = MethodChannel('com.handsfree.controller/actions');

  // Check if system accessibility service is turned on
  static Future<bool> isAccessibilityEnabled() async {
    try {
      final bool enabled = await _channel.invokeMethod('checkAccessibilityPermission');
      return enabled;
    } on PlatformException catch (_) {
      return false;
    }
  }

  // Direct user to open Accessibility Settings
  static Future<void> openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod('openAccessibilitySettings');
    } on PlatformException catch (e) {
      print("Failed to open accessibility settings: ${e.message}");
    }
  }

  // Check if overlay permission (draw over other apps) is enabled
  static Future<bool> isOverlayEnabled() async {
    try {
      final bool enabled = await _channel.invokeMethod('checkOverlayPermission');
      return enabled;
    } on PlatformException catch (_) {
      return false;
    }
  }

  // Direct user to open Draw Over Other Apps Settings
  static Future<void> openOverlaySettings() async {
    try {
      await _channel.invokeMethod('openOverlaySettings');
    } on PlatformException catch (e) {
      print("Failed to open overlay settings: ${e.message}");
    }
  }

  // Start the background foreground camera/microphone tracking service
  static Future<void> startBackgroundService() async {
    try {
      await _channel.invokeMethod('startService');
    } on PlatformException catch (e) {
      print("Failed to start background service: ${e.message}");
    }
  }

  // Stop the background service
  static Future<void> stopBackgroundService() async {
    try {
      await _channel.invokeMethod('stopService');
    } on PlatformException catch (e) {
      print("Failed to stop background service: ${e.message}");
    }
  }

  // Execute system-wide gesture actions via accessibility service
  static Future<void> executeGestureAction({
    required String action,
    double? x,
    double? y,
    double? startX,
    double? startY,
    double? endX,
    double? endY,
    int? duration,
    String? text,
  }) async {
    try {
      final Map<String, dynamic> arguments = {
        'action': action,
      };
      if (x != null) arguments['x'] = x;
      if (y != null) arguments['y'] = y;
      if (startX != null) arguments['startX'] = startX;
      if (startY != null) arguments['startY'] = startY;
      if (endX != null) arguments['endX'] = endX;
      if (endY != null) arguments['endY'] = endY;
      if (duration != null) arguments['duration'] = duration;
      if (text != null) arguments['text'] = text;

      await _channel.invokeMethod('executeAction', arguments);
    } on PlatformException catch (e) {
      print("Failed to execute gesture action: ${e.message}");
    }
  }
}
