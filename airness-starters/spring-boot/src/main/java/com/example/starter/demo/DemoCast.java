package com.example.starter.demo;

import com.example.starter.identity.Role;
import com.example.starter.task.TaskStatus;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * The people of the demonstration data: a small company's operations lead, its engineer, designer, support
 * and marketing, and one person who has left. Each keeps tasks in every state, some due today, some past
 * their day and some well ahead, so every list, filter and mark the pages draw has something to show.
 */
@UtilityClass
final class DemoCast {

    /**
     * Everybody the demonstration data admits, in the order they are admitted.
     */
    static final List<DemoPerson> PEOPLE = List.of(
        new DemoPerson(
            "Maja Kowalska", "maja.kowalska", Role.ADMIN, false, List.of(
                new DemoTask(
                    "Prepare the Q4 budget review",
                    "Collect the department forecasts and reconcile them with the spending so far.",
                    TaskStatus.IN_PROGRESS, 5
                ),
                new DemoTask(
                    "Renew the office lease",
                    "The lease ends in January. Compare the landlord's offer with two alternatives before signing.",
                    TaskStatus.OPEN, 21
                ),
                new DemoTask(
                    "Approve the holiday rota",
                    "Check that every team keeps one person on call between Christmas and New Year.",
                    TaskStatus.OPEN, -2
                ),
                new DemoTask(
                    "Onboard the new support hire",
                    "Accounts, a laptop and a plan for the first week, agreed with Piotr.",
                    TaskStatus.DONE, -10
                ),
                new DemoTask(
                    "Review the security audit findings",
                    "Give every finding an owner and agree a deadline with each team.",
                    TaskStatus.IN_PROGRESS, 3
                ),
                new DemoTask(
                    "Book a venue for the team offsite",
                    "Thirty people for two days, reachable by train from Warsaw.",
                    TaskStatus.OPEN, 45
                )
            )
        ),
        new DemoPerson(
            "Tomasz Nowak", "tomasz.nowak", Role.USER, false, List.of(
                new DemoTask(
                    "Move invoicing to the new payment provider",
                    "Run both providers side by side for a week, then make the new one the default.",
                    TaskStatus.IN_PROGRESS, 7
                ),
                new DemoTask(
                    "Fix the timeout on the export endpoint",
                    "Large exports take longer than thirty seconds. Stream the file instead of building it in memory.",
                    TaskStatus.OPEN, -1
                ),
                new DemoTask(
                    "Upgrade the database to PostgreSQL 18",
                    "Rehearse on a copy of production first and write down the steps to roll back.",
                    TaskStatus.OPEN, 30
                ),
                new DemoTask(
                    "Index the order search",
                    "Searching orders by customer name reads the whole table.",
                    TaskStatus.DONE, -6
                ),
                new DemoTask(
                    "Write up Monday's outage",
                    "The timeline, the cause and the follow-up actions agreed in the review.",
                    TaskStatus.DONE, -3
                ),
                new DemoTask(
                    "Review the checkout redesign",
                    "Look hardest at the error messages and at what happens on a slow connection.",
                    TaskStatus.OPEN, 2
                ),
                new DemoTask(
                    "Rotate the API keys of the shipping partner",
                    "The current keys were shared in a support ticket and have to be replaced.",
                    TaskStatus.OPEN, -4
                )
            )
        ),
        new DemoPerson(
            "Emily Carter", "emily.carter", Role.USER, false, List.of(
                new DemoTask(
                    "Redesign the checkout page",
                    "Fewer fields, a clear total and the delivery date shown before payment.",
                    TaskStatus.IN_PROGRESS, 4
                ),
                new DemoTask(
                    "Draw the icons for the mobile app",
                    "Twenty-four icons in two sizes, exported as SVG.",
                    TaskStatus.OPEN, 12
                ),
                new DemoTask(
                    "Test the new navigation with five customers",
                    "Recruit them through the newsletter and record the sessions with their consent.",
                    TaskStatus.OPEN, 18
                ),
                new DemoTask(
                    "Update the colours in the style guide",
                    "The new palette was approved last week.",
                    TaskStatus.DONE, -8
                ),
                new DemoTask(
                    "Check the accessibility of the sign-up form",
                    "Contrast, labels and the order the keyboard moves through the fields.",
                    TaskStatus.OPEN, -3
                ),
                new DemoTask(
                    "Design the banner for the winter campaign",
                    "Two variants, so Sophie can test which one people click.",
                    TaskStatus.OPEN, 9
                )
            )
        ),
        new DemoPerson(
            "Piotr Wiśniewski", "piotr.wisniewski", Role.USER, false, List.of(
                new DemoTask(
                    "Clear the backlog of support tickets",
                    "Forty tickets are older than three days. Answer the paying customers first.",
                    TaskStatus.IN_PROGRESS, 1
                ),
                new DemoTask(
                    "Write a help article on resetting a password",
                    "With screenshots in both English and Polish.",
                    TaskStatus.OPEN, 6
                ),
                new DemoTask(
                    "Call the customer charged twice",
                    "Finance has confirmed the refund, and the customer should hear it from us.",
                    TaskStatus.OPEN, 0
                ),
                new DemoTask(
                    "Sort last month's tickets by topic",
                    "So the help pages can answer the questions people ask most.",
                    TaskStatus.DONE, -5
                ),
                new DemoTask(
                    "Translate the questions and answers page into Polish",
                    "Fourteen questions. Keep the tone as informal as the English one.",
                    TaskStatus.OPEN, -6
                ),
                new DemoTask(
                    "Follow Tomasz through the next release",
                    "Learn which checks support runs once a release is out.",
                    TaskStatus.OPEN, 14
                )
            )
        ),
        new DemoPerson(
            "Sophie Turner", "sophie.turner", Role.USER, false, List.of(
                new DemoTask(
                    "Plan the December newsletter",
                    "Three stories, one product update and the opening hours over the holidays.",
                    TaskStatus.IN_PROGRESS, 8
                ),
                new DemoTask(
                    "Report on the autumn campaign",
                    "Reach, clicks and sign-ups, compared with the spring campaign.",
                    TaskStatus.DONE, -2
                ),
                new DemoTask(
                    "Test two headlines on the landing page",
                    "Run it for two weeks with at least a thousand visitors for each headline.",
                    TaskStatus.OPEN, 15
                ),
                new DemoTask(
                    "Agree the conference sponsorship",
                    "The silver package, with a stand near the entrance if they have one.",
                    TaskStatus.OPEN, -1
                ),
                new DemoTask(
                    "Refresh the customer stories",
                    "Two of the four quote people who have since left those companies.",
                    TaskStatus.OPEN, 25
                )
            )
        ),
        new DemoPerson(
            "Jan Zieliński", "jan.zielinski", Role.USER, true, List.of(
                new DemoTask(
                    "Hand over the reporting scripts",
                    "Write down where each script runs and who reads what it produces.",
                    TaskStatus.DONE, -14
                ),
                new DemoTask(
                    "Archive the old project folders",
                    "Everything from before 2024 moves to cold storage.",
                    TaskStatus.DONE, -12
                ),
                new DemoTask(
                    "Return the laptop and the access card",
                    "Maja collects both on the last day.",
                    TaskStatus.DONE, -9
                )
            )
        )
    );
}
