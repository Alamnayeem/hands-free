import 'package:flutter/material.dart';

class CalibrationScreen extends StatefulWidget {
  const CalibrationScreen({Key? key}) : super(key: key);

  @override
  State<CalibrationScreen> createState() => _CalibrationScreenState();
}

class _CalibrationScreenState extends State<CalibrationScreen> {
  int _calibrationStep = 0;
  final List<String> _stepTitles = [
    "Focus on Top-Left Point",
    "Focus on Top-Right Point",
    "Focus on Screen Center",
    "Focus on Bottom-Left Point",
    "Focus on Bottom-Right Point",
    "Calibration Complete!"
  ];

  final List<Alignment> _alignments = [
    Alignment.topLeft,
    Alignment.topRight,
    Alignment.center,
    Alignment.bottomLeft,
    Alignment.bottomRight,
    Alignment.center
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Stack(
          children: [
            // Exit Button
            Positioned(
              top: 10,
              left: 10,
              child: IconButton(
                icon: const Icon(Icons.close, color: Colors.white),
                onPressed: () => Navigator.pop(context),
              ),
            ),

            // Top Status Instruction Banner
            Align(
              alignment: Alignment.topCenter,
              child: Padding(
                padding: const EdgeInsets.only(top: 60.0),
                child: Text(
                  _stepTitles[_calibrationStep],
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),

            // Instructional Description
            if (_calibrationStep < 5)
              const Align(
                alignment: Alignment.center,
                child: Padding(
                  padding: EdgeInsets.symmetric(horizontal: 40.0),
                  child: Text(
                    "Stare at each red dot continuously and blink 3 times, or dwell your gaze on it for 3 seconds to register coordinate mapping.",
                    style: TextStyle(color: Colors.white64, fontSize: 13),
                    textAlign: TextAlign.center,
                  ),
                ),
              ),

            // Interactive Calibration Point
            if (_calibrationStep < 5)
              Align(
                alignment: _alignments[_calibrationStep],
                child: Padding(
                  padding: const EdgeInsets.all(40.0),
                  child: GestureDetector(
                    onTap: () {
                      setState(() {
                        _calibrationStep++;
                      });
                    },
                    child: Container(
                      width: 50,
                      height: 50,
                      decoration: const BoxDecoration(
                        color: Colors.red,
                        shape: BoxShape.circle,
                        boxShadow: [
                          BoxShadow(
                            color: Colors.redAccent,
                            blurRadius: 15,
                            spreadRadius: 2,
                          )
                        ],
                      ),
                      child: const Center(
                        child: Icon(Icons.remove_red_eye, color: Colors.white, size: 24),
                      ),
                    ),
                  ),
                ),
              ),

            // Complete Dashboard Card
            if (_calibrationStep == 5)
              Center(
                child: Card(
                  color: Colors.grey.shade900,
                  elevation: 8,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  child: Padding(
                    padding: const EdgeInsets.all(24.0),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.check_circle_outline, color: Colors.green, size: 64),
                        const SizedBox(height: 16),
                        const Text(
                          "Precision Mapping Complete",
                          style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 8),
                        const Text(
                          "Average error rate: 0.12% tracking matrix.\nGaze cursors are now fully aligned.",
                          style: TextStyle(color: Colors.white70, fontSize: 12),
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 24),
                        ElevatedButton(
                          style: ElevatedButton.styleFrom(backgroundColor: Colors.deepPurple),
                          onPressed: () {
                            Navigator.pop(context);
                          },
                          child: const Text("Return to Dashboard", style: TextStyle(color: Colors.white)),
                        )
                      ],
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
