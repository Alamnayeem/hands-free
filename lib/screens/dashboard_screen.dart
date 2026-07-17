import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../main.dart';
import '../services/method_channel_service.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({Key? key}) : super(key: key);

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  bool _accessibilityEnabled = false;
  bool _overlayEnabled = false;

  @override
  void initState() {
    super.initState();
    _checkSystemPermissions();
  }

  Future<void> _checkSystemPermissions() async {
    final acc = await MethodChannelService.isAccessibilityEnabled();
    final over = await MethodChannelService.isOverlayEnabled();
    setState(() {
      _accessibilityEnabled = acc;
      _overlayEnabled = over;
    });
  }

  @override
  Widget build(BuildContext context) {
    final state = Provider.of<HandsFreeStateProvider>(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Hands-Free Phone Controller'),
        centerTitle: true,
        actions: [
          IconButton(
            icon: Icon(state.isDarkMode ? Icons.light_mode : Icons.dark_mode),
            onPressed: () => state.toggleTheme(),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Active Service Card
            _buildServiceStatusCard(state),
            const SizedBox(height: 16),

            // Active Input Channels Toggle Grid
            _buildInputChannelsSection(state),
            const SizedBox(height: 20),

            // System Permissions Manager Section
            _buildPermissionsSection(),
            const SizedBox(height: 20),

            // Quick Operations Navigation Grid
            _buildNavigationGrid(context),
          ],
        ),
      ),
    );
  }

  Widget _buildServiceStatusCard(HandsFreeStateProvider state) {
    final isActive = state.isServiceActive;
    return Card(
      elevation: 4,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      color: isActive ? Colors.deepPurple.shade900 : Colors.grey.shade900,
      child: Padding(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      isActive ? 'SYSTEM ACTIVE' : 'SYSTEM INACTIVE',
                      style: const TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      isActive ? 'Running system-wide overlays' : 'Service stopped',
                      style: TextStyle(color: Colors.white.withOpacity(0.8), fontSize: 13),
                    ),
                  ],
                ),
                Switch(
                  value: isActive,
                  activeColor: Colors.purpleAccent,
                  onChanged: (val) async {
                    if (val) {
                      // Trigger native foreground service
                      await MethodChannelService.startBackgroundService();
                    } else {
                      await MethodChannelService.stopBackgroundService();
                    }
                    state.toggleService();
                  },
                )
              ],
            ),
            const Divider(color: Colors.white24, height: 24),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildStatusIndicator("Eyes", state.isEyeTrackingActive ? Colors.green : Colors.red),
                _buildStatusIndicator("Voice", state.isVoiceCommandActive ? Colors.green : Colors.red),
                _buildStatusIndicator("Overlay", _overlayEnabled ? Colors.green : Colors.orange),
                _buildStatusIndicator("Accessibility", _accessibilityEnabled ? Colors.green : Colors.orange),
              ],
            )
          ],
        ),
      ),
    );
  }

  Widget _buildStatusIndicator(String label, Color color) {
    return Row(
      children: [
        Container(
          width: 10,
          height: 10,
          decoration: BoxDecoration(shape: BoxShape.circle, color: color),
        ),
        const SizedBox(width: 6),
        Text(
          label,
          style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold),
        ),
      ],
    );
  }

  Widget _buildInputChannelsSection(HandsFreeStateProvider state) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          "Tracking Input Methods",
          style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              child: _buildInputToggleTile(
                icon: Icons.remove_red_eye,
                title: "Eye Tracking",
                value: state.isEyeTrackingActive,
                onChanged: (val) => state.setEyeTracking(val),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: _buildInputToggleTile(
                icon: Icons.mic,
                title: "Voice Command",
                value: state.isVoiceCommandActive,
                onChanged: (val) => state.setVoiceCommand(val),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildInputToggleTile({
    required IconData icon,
    required String title,
    required bool value,
    required Function(bool) onChanged,
  }) {
    return Card(
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12.0, vertical: 16.0),
        child: Column(
          children: [
            Icon(icon, size: 36, color: value ? Colors.deepPurple : Colors.grey),
            const SizedBox(height: 8),
            Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
            const SizedBox(height: 8),
            Switch(value: value, onChanged: onChanged),
          ],
        ),
      ),
    );
  }

  Widget _buildPermissionsSection() {
    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              "System Permissions Required",
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 12),
            _buildPermissionTile(
              title: "Accessibility Service",
              subtitle: "Required to dispatch gestures & clicks system-wide.",
              granted: _accessibilityEnabled,
              onTap: () async {
                await MethodChannelService.openAccessibilitySettings();
                _checkSystemPermissions();
              },
            ),
            const Divider(),
            _buildPermissionTile(
              title: "Overlay Window Permission",
              subtitle: "Required to draw target cursor & floating panels.",
              granted: _overlayEnabled,
              onTap: () async {
                await MethodChannelService.openOverlaySettings();
                _checkSystemPermissions();
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPermissionTile({
    required String title,
    required String subtitle,
    required bool granted,
    required VoidCallback onTap,
  }) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
      subtitle: Text(subtitle, style: const TextStyle(fontSize: 11)),
      trailing: ElevatedButton(
        style: ElevatedButton.styleFrom(
          backgroundColor: granted ? Colors.green.withOpacity(0.1) : Colors.deepPurple,
          foregroundColor: granted ? Colors.green : Colors.white,
          elevation: 0,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
        ),
        onPressed: onTap,
        child: Text(granted ? "ACTIVE" : "GRANT", style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
      ),
    );
  }

  Widget _buildNavigationGrid(BuildContext context) {
    return GridView.count(
      crossAxisCount: 3,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      crossAxisSpacing: 10,
      mainAxisSpacing: 10,
      childAspectRatio: 1.0,
      children: [
        _buildNavButton(
          icon: Icons.center_focus_strong,
          label: "Calibration",
          route: '/calibration',
          context: context,
        ),
        _buildNavButton(
          icon: Icons.history,
          label: "Logs",
          route: '/history',
          context: context,
        ),
        _buildNavButton(
          icon: Icons.settings,
          label: "Settings",
          route: '/settings',
          context: context,
        ),
      ],
    );
  }

  Widget _buildNavButton({
    required IconData icon,
    required String label,
    required String route,
    required BuildContext context,
  }) {
    return InkWell(
      onTap: () => Navigator.pushNamed(context, route),
      borderRadius: BorderRadius.circular(12),
      child: Card(
        elevation: 1,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 28, color: Colors.deepPurple),
            const SizedBox(height: 6),
            Text(
              label,
              style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
