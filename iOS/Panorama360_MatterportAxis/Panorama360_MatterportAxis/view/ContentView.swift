//
//  ContentView.swift
//  Panorama360_MatterportAxis
//
//  Created by RyoTN on 2024/03/22.
//

import SwiftUI

struct ContentView: View {
    @StateObject var model = ContentViewModel(isPreview: false)
    @Environment(\.scenePhase) var scenePhase

    var body: some View {
        NavigationView {
            VStack {
                VStack {
                    HStack {
                        if model.isConnected {
                            Button("", systemImage: "wifi") {
                                model.connectMatterportAxis()
                            }
                            .disabled(!model.isBtStandby)
                            .font(.system(size: 25))
                            .padding(.leading)
                        } else {
                            Button("", systemImage: "wifi.slash") {
                                model.connectMatterportAxis()
                            }
                            .disabled(!model.isBtStandby)
                            .font(.system(size: 25))
                            .padding(.leading)
                        }
                        Spacer()
                        NavigationLink(destination: SettingView()) {
                            Image(systemName: "gear")
                        }
                        .font(.system(size: 25))
                        .padding(.trailing)
                    }
                    HStack {
                        Slider(value: $model.mFocus,
                               in: 0 ... 1,
                               step: 0.1,
                               onEditingChanged: { _ in
                                   model.setFocus()
                               })
                               .padding(.horizontal)
                               .padding(.bottom)

                        Text(String(format: "%.1f", model.mFocus))
                            .frame(width: 50)
                            .padding(.trailing)
                            .padding(.bottom)
                    }
                }
                .background(.regularMaterial)

                Spacer()

                CameraView(previewUIView: model.mPreviewView)

                Spacer()

                VStack {
                    HStack {
                        Spacer()
                            .overlay {
                                Text(String(format: "%d°", model.mAngle))
                            }
                        Menu(model.EXPOSURE_MODES_LABEL[model.mExposureBracketMode]) {
                            Section("Exposure Bracket Mode") {
                                ForEach(Array(model.EXPOSURE_MODES_LABEL.enumerated()), id: \.element) { index, mode in
                                    Button(mode) {
                                        model.chengeExposureBracketMode(mode: index)
                                    }
                                }
                            }
                        }
                        .frame(minWidth: 100)
                        .buttonStyle(.borderedProminent)
                        .padding()

                        Spacer()
                            .overlay {
                                Button(action: {
                                    model.createDir()
                                }, label: {
                                    VStack {
                                        Image(systemName: "folder.badge.plus")
                                        Text("Create Dir")
                                            .font(.system(size: 10))
                                    }
                                })
                            }
                    }
                    HStack {
                        Spacer()
                            .overlay {
                                Button(action: {
                                    model.resetAngle()
                                }, label: {
                                    VStack {
                                        Image(systemName: "rotate.3d")
                                            .font(.system(size: 50))
                                        Text("Reset")
                                    }
                                })
                                .disabled(!model.isBtStandby || !model.isConnected)
                            }

                        Button("", systemImage: model.isCapture ? "stop.circle" : "record.circle") {
                            model.startCapture()
                        }
                        .font(.system(size: 75))

                        Spacer()
                            .overlay {
                                Button(action: {
                                    model.changeLens()
                                }, label: {
                                    VStack {
                                        Image(systemName: "arrow.triangle.2.circlepath.camera")
                                            .font(.system(size: 50))
                                        Text(model.CAMERA_TYPE_LABEL[model.mCameraType.rawValue])
                                    }
                                })
                            }
                    }
                    .padding(.top)
                }
                .background(.regularMaterial)
                .frame(maxWidth: .infinity)
            }
        }
        .foregroundStyle(.foreground)
        .overlay {
            ZStack {
                if model.isToastShown {
                    ToastView(text: model.mToastMsg, isShown: $model.isToastShown)
                }
            }
        }
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .active:
                model.setInitalVolume()
            case .background:
                model.stopListeningVolume()
            case .inactive: break
            @unknown default: break
            }
        }
    }
}

#Preview {
    ContentView(model: ContentViewModel(isPreview: true))
}
