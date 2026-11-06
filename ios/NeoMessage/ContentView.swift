//
//  ContentView.swift
//  NeoMessage - Motor de Ejecución Soberana en iOS
//

import SwiftUI
import WebKit
import CoreLocation
import AVFoundation

struct ContentView: View {
    @StateObject private var locationDelegate = LocationManager()
    
    var body: some View {
        ZStack {
            Color(red: 17/255, green: 27/255, blue: 33/255)
                .edgesIgnoringSafeArea(.all)
            
            NeoWebView()
                .edgesIgnoringSafeArea(.all)
        }
    }
}

struct NeoWebView: UIViewRepresentable {
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        
        let prefs = WKWebpagePreferences()
        prefs.allowsContentJavaScript = true
        config.defaultWebpagePreferences = prefs
        
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.isOpaque = false
        webView.backgroundColor = UIColor(red: 17/255, green: 27/255, blue: 33/255, alpha: 1.0)
        webView.scrollView.bounces = false
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        
        // Carga online soberana con fallback local
        if let onlineUrl = URL(string: "https://josuehg59-star.github.io/NeoMessage/?platform=ios") {
            let request = URLRequest(url: onlineUrl, cachePolicy: .useProtocolCachePolicy, timeoutInterval: 15)
            webView.load(request)
        } else if let localPath = Bundle.main.path(forResource: "index", ofType: "html") {
            let localUrl = URL(fileURLWithPath: localPath)
            webView.loadFileURL(localUrl, allowingReadAccessTo: localUrl.deletingLastPathComponent())
        }
        
        return webView
    }
    
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    
    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.requestWhenInUseAuthorization()
    }
}
