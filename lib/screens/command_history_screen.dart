import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../main.dart';

class CommandHistoryScreen extends StatelessWidget {
  const CommandHistoryScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final state = Provider.of<HandsFreeStateProvider>(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Action Command Logs'),
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(Icons.delete_outline),
            onPressed: () {
              state.clearHistory();
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text("All command execution history cleared.")),
              );
            },
          ),
        ],
      ),
      body: state.history.isEmpty
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.receipt_long, size: 64, color: Colors.grey.shade700),
                  const SizedBox(height: 16),
                  const Text(
                    "No Commands Logged yet",
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.grey),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    "Commands spoken or eye gestures executed will register here.",
                    style: TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                ],
              ),
            )
          : ListView.builder(
              padding: const EdgeInsets.all(12.0),
              itemCount: state.history.length,
              itemBuilder: (context, index) {
                final log = state.history[index];
                final success = log['success'] as bool;
                final timestamp = DateTime.parse(log['timestamp'] as String);

                return Card(
                  elevation: 1,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  child: ListTile(
                    leading: CircleAvatar(
                      backgroundColor: success ? Colors.green.withOpacity(0.1) : Colors.red.withOpacity(0.1),
                      child: Icon(
                        success ? Icons.check : Icons.close,
                        color: success ? Colors.green : Colors.red,
                        size: 20,
                      ),
                    ),
                    title: Text(
                      log['phrase'] as String,
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                    ),
                    subtitle: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const SizedBox(height: 4),
                        Text(
                          "Action: ${log['action']}",
                          style: TextStyle(color: Colors.deepPurple.shade300, fontSize: 11, fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          "${timestamp.hour}:${timestamp.minute.toString().padLeft(2, '0')}:${timestamp.second.toString().padLeft(2, '0')}",
                          style: const TextStyle(fontSize: 10, color: Colors.grey),
                        ),
                      ],
                    ),
                    trailing: Text(
                      success ? "SUCCESS" : "FAILED",
                      style: TextStyle(
                        color: success ? Colors.green : Colors.red,
                        fontSize: 10,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                );
              },
            ),
    );
  }
}
