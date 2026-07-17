import 'package:flutter/material.dart';
import '../services/method_channel_service.dart';

class HandsFreeFloatingPanel extends StatelessWidget {
  const HandsFreeFloatingPanel({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: Colors.black.withOpacity(0.85),
          borderRadius: BorderRadius.circular(30),
          border: Border.all(color: Colors.white24, width: 1),
          boxShadow: const [
            BoxShadow(
              color: Colors.black54,
              blurRadius: 10,
              offset: Offset(0, 4),
            )
          ],
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            _buildFloatingActionButton(
              icon: Icons.home,
              label: "Home",
              onTap: () => MethodChannelService.executeGestureAction(action: "HOME"),
            ),
            const SizedBox(width: 8),
            _buildFloatingActionButton(
              icon: Icons.arrow_back,
              label: "Back",
              onTap: () => MethodChannelService.executeGestureAction(action: "BACK"),
            ),
            const SizedBox(width: 8),
            _buildFloatingActionButton(
              icon: Icons.screenshot,
              label: "Screenshot",
              onTap: () => MethodChannelService.executeGestureAction(action: "SCREENSHOT"),
            ),
            const SizedBox(width: 8),
            _buildFloatingActionButton(
              icon: Icons.volume_up,
              label: "Vol +",
              onTap: () => MethodChannelService.executeGestureAction(action: "VOLUME_UP"),
            ),
            const SizedBox(width: 8),
            _buildFloatingActionButton(
              icon: Icons.volume_down,
              label: "Vol -",
              onTap: () => MethodChannelService.executeGestureAction(action: "VOLUME_DOWN"),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFloatingActionButton({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: Colors.deepPurple.withOpacity(0.3),
            ),
            child: Icon(icon, color: Colors.white, size: 20),
          ),
          const SizedBox(height: 2),
          Text(
            label,
            style: const TextStyle(color: Colors.white, fontSize: 8, fontWeight: FontWeight.bold),
          ),
        ],
      ),
    );
  }
}
