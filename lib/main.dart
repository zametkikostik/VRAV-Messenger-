import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

void main() {
  runApp(const VravApp());
}

class VravApp extends StatelessWidget {
  const VravApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'VRAV Messenger',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF6200EE),
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      home: const HomeScreen(),
    );
  }
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  bool _isPurging = false;

  Future<void> _triggerPanicPurge() async {
    setState(() {
      _isPurging = true;
    });

    try {
      final dbFolder = await getApplicationDocumentsDirectory();
      final String sqlitePath = "${dbFolder.path}/vrav_secure_chat.db";

      const MethodChannel vravChannel = MethodChannel('com.vrav.secure/crypto');
      final bool? success = await vravChannel.invokeMethod<bool>('triggerPanicProtocol', {
        'db_path': sqlitePath,
      });

      if (success == true) {
        debugPrint("🔒 Cryptographic panic purge completed successfully.");
      }
      
      // Exit application cleanly
      await SystemChannels.platform.invokeMethod('SystemNavigator.pop');
    } catch (e) {
      debugPrint("CRITICAL: Secure sanitization failed: $e");
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text("Sanitization failed: $e"),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _isPurging = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('🔒 VRAV Messenger'),
        centerTitle: true,
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: <Widget>[
              const Icon(
                Icons.security_rounded,
                size: 80,
                color: Colors.greenAccent,
              ),
              const SizedBox(height: 24),
              const Text(
                'High-Security Hybrid PPQ Chat',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 12),
              const Text(
                'Secured with Post-Quantum Kyber-768 & Hybrid Double Ratchet.',
                style: TextStyle(color: Colors.grey),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 48),
              if (_isPurging)
                const CircularProgressIndicator(color: Colors.redAccent)
              else
                ElevatedButton.icon(
                  onPressed: _triggerPanicPurge,
                  icon: const Icon(Icons.warning, color: Colors.white),
                  label: const Text('ACTIVATE PANIC BUTTON'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.red,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                    textStyle: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
