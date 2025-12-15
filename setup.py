from setuptools import setup, find_packages

with open("requirements.txt") as f:
    install_requires = f.read().strip().split("\n")

# Get version from __version__ variable in frappe_fcm/__init__.py
from frappe_fcm import __version__ as version

setup(
    name="frappe_fcm",
    version=version,
    description="Firebase Cloud Messaging (FCM) Push Notifications for Frappe",
    author="Frappe FCM Contributors",
    author_email="hello@frappe.io",
    packages=find_packages(),
    zip_safe=False,
    include_package_data=True,
    install_requires=install_requires,
    python_requires=">=3.10",
    classifiers=[
        "Development Status :: 4 - Beta",
        "Environment :: Web Environment",
        "Framework :: Frappe",
        "Intended Audience :: Developers",
        "License :: OSI Approved :: MIT License",
        "Operating System :: OS Independent",
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.10",
        "Programming Language :: Python :: 3.11",
        "Topic :: Communications",
        "Topic :: Internet :: WWW/HTTP",
    ],
)
