import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../main.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final state = Provider.of<HandsFreeStateProvider>(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('System Settings'),
        centerTitle: true,
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          // Eye Tracking Settings
          _buildSectionHeader("EYE TRACKING THRESHOLDS"),
          _buildSliderTile(
            title: "Eye Gaze Sensitivity",
            subtitle: "Increases coordinate tracking velocity.",
            value: state.eyeSensitivity,
            min: 0.5,
            max: 2.0,
            onChanged: (val) => state.updateEyeSensitivity(val),
          ),
          _buildSliderTile(
            title: "Blink Detection Threshold",
            subtitle: "Sensitises eyelid snap closures.",
            value: state.blinkSensitivity,
            min: 0.1,
            max: 1.0,
            onChanged: (val) => state.updateBlinkSensitivity(val),
          ),
          _buildSliderTile(
            title: "Cursor Movement Speed",
            value: state.cursorSpeed,
            min: 0.5,
            max: 3.0,
            onChanged: (val) => state.updateCursorSpeed(val),
          ),
          _buildDwellTimeTile(state),

          const SizedBox(height: 16),

          // Voice Command Settings
          _buildSectionHeader("VOICE RECOGNITION CONFIGS"),
          _buildSliderTile(
            title: "Noise Filter Cutoff",
            subtitle: "Filters microphone static ambience.",
            value: state.voiceSensitivity,
            min: 0.1,
            max: 1.0,
            onChanged: (val) => state.updateVoiceSensitivity(val),
          ),
          ListTile(
            title: const Text("Language Recognition", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
            subtitle: const Text("Dual English + Bengali auto-interpretation mode is continuous."),
            trailing: Text(
              "Duo Enabled",
              style: TextStyle(color: Colors.deepPurple.shade300, fontWeight: FontWeight.bold, fontSize: 13),
            ),
          ),

          const SizedBox(height: 16),

          // Customization settings
          _buildSectionHeader("INTERFACE CUSTOMISATION"),
          _buildSliderTile(
            title: "Floating Controls Overlay Size",
            value: state.overlaySize,
            min: 30.0,
            max: 100.0,
            onChanged: (val) => state.updateOverlaySize(val),
          ),
          SwitchListTile(
            title: const Text("Dark Visual Theme", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
            value: state.isDarkMode,
            onChanged: (val) => state.toggleTheme(),
          ),

          const SizedBox(height: 24),

          // Reset settings button
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0),
            child: OutlinedButton(
              style: OutlinedButton.styleFrom(
                foregroundColor: Colors.redAccent,
                side: const BorderSide(color: Colors.redAccent),
              ),
              onPressed: () {
                state.updateEyeSensitivity(1.0);
                state.updateBlinkSensitivity(0.8);
                state.updateCursorSpeed(1.2);
                state.updateDwellTime(800);
                state.updateVoiceSensitivity(0.7);
                state.updateOverlaySize(50.0);
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text("All threshold settings reset to factory defaults.")),
                );
              },
              child: const Text("RESET ALL CALIBRATIONS & CONFIGS"),
            ),
          ),
          const SizedBox(height: 40),
        ],
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Text(
        title,
        style: const TextStyle(
          color: Colors.grey,
          fontSize: 12,
          fontWeight: FontWeight.bold,
          letterSpacing: 1.2,
        ),
      ),
    );
  }

  Widget _buildSliderTile({
    required String title,
    String? subtitle,
    required double value,
    required double min,
    required double max,
    required Function(double) onChanged,
  }) {
    return Card(
      elevation: 0,
      color: Colors.transparent,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 6.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                    if (subtitle != null)
                      Text(subtitle, style: const TextStyle(color: Colors.grey, fontSize: 11)),
                  ],
                ),
                Text(
                  value.toStringAsFixed(2),
                  style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.deepPurple, fontSize: 13),
                ),
              ],
            ),
            Slider(
              value: value,
              min: min,
              max: max,
              activeColor: Colors.deepPurple,
              onChanged: onChanged,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDwellTimeTile(HandsFreeStateProvider state) {
    return ListTile(
      title: const Text("Dwell Click Timer", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
      subtitle: const Text("Staring at an icon triggers automatic click."),
      trailing: DropdownButton<int>(
        value: state.dwellTimeMs,
        onChanged: (val) {
          if (val != null) state.updateDwellTime(val);
        },
        items: const [
          DropdownMenuItem(value: 500, child: Text("500 ms")),
          DropdownMenuItem(value: 800, child: Text("800 ms")),
          DropdownMenuItem(value: 1200, child: Text("1.2 sec")),
          DropdownMenuItem(value: 1500, child: Text("1.5 sec")),
          DropdownMenuItem(value: 2000, child: Text("2.0 sec")),
        ],
      ),
    );
  }
}
