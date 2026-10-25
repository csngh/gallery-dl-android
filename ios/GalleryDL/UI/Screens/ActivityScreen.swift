import SwiftUI

private enum ActivityFilter: CaseIterable {
    case all, completed, partial, failed

    var results: Set<TaskResult>? {
        switch self {
        case .all: return nil
        case .completed: return [.completed]
        case .partial: return [.partial]
        case .failed: return [.failed]
        }
    }

    var label: String {
        switch self {
        case .all: return S.filterAll
        case .completed: return S.statCompleted
        case .partial: return S.statPartial
        case .failed: return S.statFailed
        }
    }
}

struct ActivityScreen: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var store: AppStore
    @State private var filter = ActivityFilter.all

    var body: some View {
        let tasks = store.tasks
        let results = tasks.map(\.result)
        let partial = results.filter { $0 == .partial }.count
        let failed = results.filter { $0 == .failed }.count
        let retryable = partial + failed

        VStack(spacing: 0) {
            TopBar(title: S.navActivity) {
                if retryable > 0 {
                    TextPill(title: S.actionRetryFailed(retryable), symbol: "arrow.clockwise", color: ActionColors.positive) {
                        app.engine.retryAllFailed()
                    }
                }
            }
            if !store.loaded {
                ShimmerCardList(count: 4)
            } else if tasks.isEmpty {
                EmptyState(title: S.activityEmptyTitle, message: S.activityEmptyBody, symbol: "chart.bar.fill", tone: .teal)
            } else {
                SoftScrollView {
                    LazyVStack(spacing: Spacing.md) {
                        // Totals first, then how the links turned out; three cards per row keep every label readable.
                        VStack(spacing: Spacing.sm) {
                            HStack(spacing: Spacing.sm) {
                                StatCard(value: tasks.count, label: S.statLinks, symbol: "link", tone: .blue)
                                StatCard(value: tasks.reduce(0) { $0 + $1.filesDownloaded }, label: S.statFilesSaved, symbol: "square.and.arrow.down", tone: .green)
                                StatCard(value: tasks.reduce(0) { $0 + $1.failedFiles }, label: S.statFilesFailed, symbol: "xmark.square.fill", tone: .red)
                            }
                            HStack(spacing: Spacing.sm) {
                                StatCard(value: results.filter { $0 == .completed }.count, label: S.statCompleted, symbol: "checkmark.circle.fill", tone: .green)
                                StatCard(value: partial, label: S.statPartial, symbol: "exclamationmark.triangle.fill", tone: .amber)
                                StatCard(value: failed, label: S.statFailed, symbol: "exclamationmark.circle.fill", tone: .red)
                            }
                        }
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: Spacing.sm) {
                                ForEach(ActivityFilter.allCases, id: \.self) { option in
                                    FilterChip(title: option.label, selected: filter == option) { filter = option }
                                }
                            }
                        }
                        ForEach(tasks.filter { filter.results == nil || filter.results!.contains($0.result) }) { task in
                            LinkRow(task: task)
                        }
                    }
                    .padding(Spacing.md)
                }
            }
        }
    }
}

private struct StatCard: View {
    let value: Int
    let label: String
    let symbol: String
    let tone: Tone

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text("\(value)").textStyle(.headlineSmall)
                    Spacer(minLength: 0)
                    IconTile(symbol: symbol, tone: tone, size: TileSize.small)
                }
                Text(label).textStyle(.labelSmall, color: Brand.onSurfaceVariant).lineLimit(1)
            }
            .padding(Spacing.md)
            // A 3 pt edge in the tone along the top; the card clips it to its rounded corners.
            .overlay(Rectangle().fill(tone.icon).frame(height: 3), alignment: .top)
        }
    }
}

private struct LinkRow: View {
    @EnvironmentObject private var app: AppModel
    let task: DownloadTask
    @State private var expanded = false

    private static let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter
    }()

    var body: some View {
        let result = task.result
        // Every finished link can be run again; a completed one only looks for new files.
        let canRetry = result != .inProgress && result != .ready
        Card {
            VStack(alignment: .leading, spacing: 0) {
                Button { withAnimation(.easeInOut(duration: 0.25)) { expanded.toggle() } } label: {
                    HStack(spacing: Spacing.md) {
                        let (symbol, tone) = result.tile
                        IconTile(symbol: symbol, tone: tone)
                        VStack(alignment: .leading, spacing: Spacing.xs) {
                            Text(task.url).textStyle(.bodyLarge).lineLimit(expanded ? nil : 1).multilineTextAlignment(.leading)
                            HStack(spacing: Spacing.sm) {
                                Text(result.label).textStyle(.labelLarge, color: result.color)
                                if !task.summaryParts.isEmpty {
                                    Text(task.summaryParts.joined(separator: " · ")).textStyle(.labelLarge, color: Brand.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(minLength: 0)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Brand.onSurfaceVariant)
                            .rotationEffect(.degrees(expanded ? 180 : 0))
                            .accessibilityLabel(expanded ? S.actionCollapse : S.actionExpand)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                if expanded {
                    VStack(alignment: .leading, spacing: Spacing.sm) {
                        Rectangle().fill(Brand.outlineVariant).frame(height: 1)
                        DetailLine(symbol: "folder.fill", tone: .blue, label: S.detailFolder, value: linkFolderName(task.url))
                        DetailLine(symbol: "clock.fill", tone: .violet, label: S.detailAdded, value: Self.formatter.string(from: task.createdAt))
                        if let finished = task.finishedAt {
                            DetailLine(symbol: "flag.fill", tone: .teal, label: S.detailFinished, value: Self.formatter.string(from: finished))
                        }
                        DetailLine(symbol: "square.and.arrow.down", tone: .green, label: S.statFilesSaved, value: "\(task.filesDownloaded)")
                        DetailLine(symbol: "xmark.square.fill", tone: .red, label: S.statFilesFailed, value: "\(task.failedFiles)")
                        if let error = task.error { Text(error).textStyle(.bodySmall, color: Brand.error) }
                        if canRetry {
                            OutlinedPill(title: result == .completed ? S.actionCheckNew : S.actionRetry, symbol: "arrow.clockwise", color: ActionColors.positive, border: ActionColors.positive) {
                                app.engine.retry(task.id)
                            }
                        }
                    }
                    .padding(.top, Spacing.md)
                }
            }
            .padding(Spacing.md)
        }
    }
}

private struct DetailLine: View {
    let symbol: String
    let tone: Tone
    let label: String
    let value: String

    var body: some View {
        HStack(spacing: Spacing.md) {
            IconTile(symbol: symbol, tone: tone, size: TileSize.small)
            Text(label).textStyle(.labelMedium, color: Brand.onSurfaceVariant).frame(width: 72, alignment: .leading)
            Text(value).textStyle(.bodyMedium).frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
