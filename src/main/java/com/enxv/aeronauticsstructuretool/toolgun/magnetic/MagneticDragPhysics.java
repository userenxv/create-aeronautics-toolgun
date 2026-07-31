package com.enxv.aeronauticsstructuretool.toolgun.magnetic;

import com.enxv.aeronauticsstructuretool.compat.sable.SableConstraintApiBridge;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.io.IOException;

public final class MagneticDragPhysics {
    private static final double MIN_DISTANCE = 2.5D;

    private MagneticDragPhysics() {
    }

    public static PhysicsConstraintHandle createDragConstraint(
            PhysicsPipeline pipeline,
            ServerSubLevel subLevel,
            Vector3dc localGrabPoint,
            Vector3dc targetGrabPoint,
            Quaterniondc targetOrientation,
            Profile profile
    ) throws IOException {
        Quaterniond orientation = new Quaterniond(targetOrientation).normalize();
        PhysicsConstraintHandle constraint = SableConstraintApiBridge.addFree(
                pipeline,
                subLevel,
                new Vector3d(),
                new Vector3d(localGrabPoint),
                orientation
        );
        try {
            ConstraintTuning tuning = profile.tuning;
            double linearDamping = linearDampingForMass(
                    subLevel.getMassTracker().getMass(),
                    profile
            );
            for (ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
                constraint.setMotor(
                        axis,
                        0.0D,
                        tuning.angularStiffness,
                        tuning.angularDamping,
                        tuning.angularMaxForce > 0.0D,
                        tuning.angularMaxForce
                );
            }

            Vector3d jointTarget = jointLinearTarget(targetGrabPoint, orientation, new Vector3d());
            constraint.setMotor(
                    ConstraintJointAxis.LINEAR_X,
                    jointTarget.x,
                    tuning.linearStiffness,
                    linearDamping,
                    tuning.linearMaxForce > 0.0D,
                    tuning.linearMaxForce
            );
            constraint.setMotor(
                    ConstraintJointAxis.LINEAR_Y,
                    jointTarget.y,
                    tuning.linearStiffness,
                    linearDamping,
                    tuning.linearMaxForce > 0.0D,
                    tuning.linearMaxForce
            );
            constraint.setMotor(
                    ConstraintJointAxis.LINEAR_Z,
                    jointTarget.z,
                    tuning.linearStiffness,
                    linearDamping,
                    tuning.linearMaxForce > 0.0D,
                    tuning.linearMaxForce
            );
            return constraint;
        } catch (RuntimeException exception) {
            try {
                constraint.remove();
            } catch (RuntimeException removalFailure) {
                exception.addSuppressed(removalFailure);
            }
            throw new IOException("failed to configure magnetic drag constraint", exception);
        }
    }

    static Vector3d jointLinearTarget(
            Vector3dc worldTarget,
            Quaterniondc constraintOrientation,
            Vector3d destination
    ) {
        destination.set(worldTarget);
        constraintOrientation.transformInverse(destination);
        return destination;
    }

    public static void launch(ServerSubLevel subLevel, Vec3 lookDirection, Profile profile) {
        if (profile != Profile.STANDARD) {
            return;
        }
        MassData massData = subLevel.getMassTracker();
        if (massData.isInvalid() || massData.getMass() <= 0.0D) {
            return;
        }
        double mass = massData.getMass();
        ConstraintTuning tuning = profile.tuning;
        double launchSpeed = launchSpeedForMass(mass, profile);

        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        Vector3d desiredVelocity = new Vector3d(lookDirection.x, lookDirection.y, lookDirection.z)
                .normalize()
                .mul(launchSpeed);
        Vector3d deltaVelocity = desiredVelocity.sub(handle.getLinearVelocity(new Vector3d()));
        Vector3d worldImpulse = deltaVelocity.mul(mass);
        clampLength(worldImpulse, tuning.maximumLaunchImpulse);
        Vector3d localImpulse = subLevel.logicalPose().transformNormalInverse(worldImpulse, new Vector3d());
        handle.applyLinearImpulse(localImpulse);
    }

    public static void stopMotion(ServerSubLevel subLevel) {
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        handle.addLinearAndAngularVelocity(
                handle.getLinearVelocity(new Vector3d()).negate(),
                handle.getAngularVelocity(new Vector3d()).negate()
        );
    }

    public static double clampDistance(double distance, Profile profile) {
        return Math.max(MIN_DISTANCE, Math.min(distance, profile.tuning.maxDistance));
    }

    public static double maxTrackingError(Profile profile) {
        return profile.tuning.maxDistance * 2.0D + 8.0D;
    }

    public static double precisionTargetSpeed(Profile profile) {
        return profile.tuning.precisionTargetSpeed;
    }

    static double launchSpeedForMass(double mass, Profile profile) {
        ConstraintTuning tuning = profile.tuning;
        double speed = tuning.launchSpeed / Math.sqrt(
                Math.max(1.0D, Math.max(0.0D, mass) / tuning.launchReferenceMass)
        );
        return Math.max(tuning.minimumLaunchSpeed, Math.min(tuning.launchSpeed, speed));
    }

    static ConstraintTuning tuning(Profile profile) {
        return profile.tuning;
    }

    static double linearDampingForMass(double mass, Profile profile) {
        ConstraintTuning tuning = profile.tuning;
        if (profile == Profile.CREATIVE || !Double.isFinite(mass) || mass <= 0.0D) {
            return tuning.linearDamping;
        }
        double criticalDamping = 2.0D * Math.sqrt(tuning.linearStiffness * mass);
        return Math.max(tuning.linearDamping, criticalDamping * 1.1D);
    }

    private static Vector3d clampLength(Vector3d vector, double maxLength) {
        double lengthSquared = vector.lengthSquared();
        double safeMaximum = Math.max(0.0D, maxLength);
        if (lengthSquared > safeMaximum * safeMaximum && lengthSquared > 1.0E-8D) {
            vector.normalize(safeMaximum);
        }
        return vector;
    }

    public enum Profile {
        STANDARD(new ConstraintTuning(
                24.0D,
                2300.0D,
                125.0D,
                5500.0D,
                9000.0D,
                800.0D,
                24_000.0D,
                7.0D,
                42.0D,
                4.0D,
                6.0D,
                25_000.0D
        )),
        CREATIVE(new ConstraintTuning(
                256.0D,
                2650.0D,
                125.0D,
                0.0D,
                10_000.0D,
                850.0D,
                0.0D,
                7.0D,
                0.0D,
                1.0D,
                0.0D,
                0.0D
        ));

        private final ConstraintTuning tuning;

        Profile(ConstraintTuning tuning) {
            this.tuning = tuning;
        }
    }

    record ConstraintTuning(
            double maxDistance,
            double linearStiffness,
            double linearDamping,
            double linearMaxForce,
            double angularStiffness,
            double angularDamping,
            double angularMaxForce,
            double precisionTargetSpeed,
            double launchSpeed,
            double launchReferenceMass,
            double minimumLaunchSpeed,
            double maximumLaunchImpulse
    ) {
    }
}
