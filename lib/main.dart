import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:google_fonts/google_fonts.dart';

import 'models/command.dart';
import 'screens/dashboard_screen.dart';
import 'screens/calibration_screen.dart';
import 'screens/settings_screen.dart';
import 'screens/command_history_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => HandsFreeStateProvider()),
      ],
      child: const HandsFreeApp(),
    ),
  );
}

class HandsFreeApp extends StatelessWidget {
  const HandsFreeApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final state = Provider.of<HandsFreeStateProvider>(context);
    return MaterialApp(
      title: 'Hands-Free Controller',
      debugShowCheckedModeBanner: false,
      themeMode: state.isDarkMode ? ThemeMode.dark : ThemeMode.light,
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.light,
        colorSchemeSeed: Colors.deepPurple,
        textTheme: GoogleFonts.interTextTheme(ThemeData.light().textTheme),
      ),
      darkTheme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        colorSchemeSeed: Colors.deepPurple,
        textTheme: GoogleFonts.interTextTheme(ThemeData.dark().textTheme),
      ),
      initialRoute: '/',
      routes: {
        '/': (context) => const DashboardScreen(),
        '/calibration': (context) => const CalibrationScreen(),
        '/settings': (context) => const SettingsScreen(),
        '/history': (context) => const CommandHistoryScreen(),
      },
    );
  }
}

// Manages the global reactive settings and action queues for hands-free operations
class HandsFreeStateProvider extends ChangeNotifier {
  bool _isServiceActive = false;
  bool _isEyeTrackingActive = true;
  bool _isVoiceCommandActive = true;
  bool _isTextCommandActive = true;

  double _eyeSensitivity = 1.0;
  double _blinkSensitivity = 0.8;
  double _cursorSpeed = 1.2;
  int _dwellTimeMs = 800;
  double _voiceSensitivity = 0.7;

  bool _isDarkMode = true;
  bool _isEnglishMode = true; // Auto-detect handles switching, but togglable too
  double _overlaySize = 50.0;
  Offset _bubblePosition = const Offset(10, 200);

  final List<Map<String, dynamic>> _history = [];

  // Getters
  bool get isServiceActive => _isServiceActive;
  bool get isEyeTrackingActive => _isEyeTrackingActive;
  bool get isVoiceCommandActive => _isVoiceCommandActive;
  bool get isTextCommandActive => _isTextCommandActive;

  double get eyeSensitivity => _eyeSensitivity;
  double get blinkSensitivity => _blinkSensitivity;
  double get cursorSpeed => _cursorSpeed;
  int get dwellTimeMs => _dwellTimeMs;
  double get voiceSensitivity => _voiceSensitivity;

  bool get isDarkMode => _isDarkMode;
  bool get isEnglishMode => _isEnglishMode;
  double get overlaySize => _overlaySize;
  Offset get bubblePosition => _bubblePosition;
  List<Map<String, dynamic>> get history => _history;

  // Setters
  void toggleService() {
    _isServiceActive = !_isServiceActive;
    notifyListeners();
  }

  void setEyeTracking(bool active) {
    _isEyeTrackingActive = active;
    notifyListeners();
  }

  void setVoiceCommand(bool active) {
    _isVoiceCommandActive = active;
    notifyListeners();
  }

  void setTextCommand(bool active) {
    _isTextCommandActive = active;
    notifyListeners();
  }

  void updateEyeSensitivity(double val) {
    _eyeSensitivity = val;
    notifyListeners();
  }

  void updateBlinkSensitivity(double val) {
    _blinkSensitivity = val;
    notifyListeners();
  }

  void updateCursorSpeed(double val) {
    _cursorSpeed = val;
    notifyListeners();
  }

  void updateDwellTime(int val) {
    _dwellTimeMs = val;
    notifyListeners();
  }

  void updateVoiceSensitivity(double val) {
    _voiceSensitivity = val;
    notifyListeners();
  }

  void toggleTheme() {
    _isDarkMode = !_isDarkMode;
    notifyListeners();
  }

  void toggleLanguage() {
    _isEnglishMode = !_isEnglishMode;
    notifyListeners();
  }

  void updateOverlaySize(double size) {
    _overlaySize = size;
    notifyListeners();
  }

  void updateBubblePosition(Offset pos) {
    _bubblePosition = pos;
    notifyListeners();
  }

  void logCommand(String phrase, bool success, String actionExecuted) {
    _history.insert(0, {
      'timestamp': DateTime.now().toIso8601String(),
      'phrase': phrase,
      'success': success,
      'action': actionExecuted,
    });
    if (_history.length > 100) {
      _history.removeLast();
    }
    notifyListeners();
  }

  void clearHistory() {
    _history.clear();
    notifyListeners();
  }
}
