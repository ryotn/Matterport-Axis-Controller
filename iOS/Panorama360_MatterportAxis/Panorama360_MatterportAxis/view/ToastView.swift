//
//  ToastView.swift
//  Panorama360_MatterportAxis
//
//  Created by RyoTN on 2024/03/23.
//
// 元コード
// https://zenn.dev/woo_noo/articles/17f05786009d563ab498

import SwiftUI

struct ToastView: View {
  let text: String
  @Binding var isShown: Bool

  var body: some View {
    VStack {
      Spacer()
    Text(text)
        .font(.headline)
        .foregroundColor(.primary)
        .padding(20)
        .background(Color(UIColor.secondarySystemBackground))
        .clipShape(Capsule())
    }
    .frame(width: UIScreen.main.bounds.width / 1.25)
    .transition(AnyTransition.opacity)
    .onTapGesture {
      withAnimation {
        self.isShown = false
      }
    }.onAppear {
       DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
         withAnimation {
           self.isShown = false
         }
       }
    }
  }
}
